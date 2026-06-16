package ru.liko.wrbdrones.client;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.FpvDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;
import ru.liko.wrbdrones.network.DroneSignalLostPacket;
import ru.liko.wrbdrones.network.ExitDroneControlPacket;
import ru.liko.wrbdrones.network.LancetCourseCommandPacket;
import ru.liko.wrbdrones.network.LancetInputPacket;
import ru.liko.wrbdrones.network.LancetTargetPacket;
import ru.liko.wrbdrones.network.LancetStatePacket;
import ru.liko.wrbdrones.network.LancetAttackPacket;
import ru.liko.wrbdrones.network.MavicDroneFirePacket;

import java.util.UUID;
import java.util.List;
import java.util.Comparator;

@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class DroneInputHandler {

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onMouseButtonPressed(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        int button = event.getButton();
        boolean isLeft = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean isRight = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        boolean isMiddle = button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        if (!isLeft && !isRight && !isMiddle) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }

        Player player = mc.player;
        ItemStack stack = player.getMainHandItem();

        if (!stack.is(ModItems.MONITOR.get())) {
            return;
        }

        var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean("Linked") || !tag.getBoolean("Using")) {
            return;
        }

        String droneId = tag.getString("LinkedDrone");

        // ЛКМ у FPV — камикадзе. Mavic With Drop — дублируем SBW fire через свой пакет (надёжный сброс).
        if (isLeft) {
            var drone = EntityFindUtil.findDrone(player.level(), droneId);
            if (drone instanceof ZalaLancetEntity lancet) {
                if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON) {
                    int targetId = lancet.getSelectedTargetId();
                    if (targetId != -1) {
                        Entity target = lancet.level().getEntity(targetId);
                        if (target != null) {
                            Vec3 fallback = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
                            PacketDistributor.sendToServer(LancetTargetPacket.entity(lancet.getUUID(), target.getUUID(), fallback));
                            PacketDistributor.sendToServer(new LancetAttackPacket(lancet.getUUID()));
                        }
                    }
                } else {
                    sendLancetCourseCommand(lancet);
                }
                event.setCanceled(true);
                return;
            }
            if (drone instanceof FpvDroneEntity) {
                try {
                    UUID droneUUID = UUID.fromString(droneId);
                    PacketDistributor.sendToServer(new DroneSignalLostPacket(droneUUID, true));
                } catch (IllegalArgumentException ignored) {
                }
                event.setCanceled(true);
                return;
            }
            if (drone instanceof MavicDroneWithDropEntity) {
                trySendMavicDropFire(player);
            }
            return;
        }

        if (isMiddle) {
            var drone = EntityFindUtil.findDrone(player.level(), droneId);
            if (drone instanceof ZalaLancetEntity lancet) {
                sendLancetTarget(lancet);
            }
            event.setCanceled(true);
            return;
        }

        // ПКМ — выход из управления дроном
        tag.putBoolean("Using", false);
        NBTTool.saveTag(stack, tag);

        if (ClientEventHandler.lastCameraType != null) {
            mc.options.setCameraType(ClientEventHandler.lastCameraType);
        } else {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        PacketDistributor.sendToServer(new ExitDroneControlPacket());
        event.setCanceled(true);
    }

    /**
     * Атака с клавиатуры (не мышь): SBW может не вызвать {@code droneLeftClick}, но сброс нужен тем же правилам.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.NORMAL)
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        Player player = mc.player;
        if (player.isSpectator()) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return;
        }

        var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean("Linked") || !tag.getBoolean("Using")) {
            return;
        }

        String droneId = tag.getString("LinkedDrone");
        var drone = EntityFindUtil.findDrone(player.level(), droneId);
        if (drone instanceof ZalaLancetEntity lancet) {
            if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON) {
                int targetId = lancet.getSelectedTargetId();
                if (targetId != -1) {
                    Entity target = lancet.level().getEntity(targetId);
                    if (target != null) {
                        Vec3 fallback = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
                        PacketDistributor.sendToServer(LancetTargetPacket.entity(lancet.getUUID(), target.getUUID(), fallback));
                        PacketDistributor.sendToServer(new LancetAttackPacket(lancet.getUUID()));
                    }
                }
            } else {
                sendLancetCourseCommand(lancet);
            }
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (drone instanceof FpvDroneEntity) {
            return;
        }
        if (drone instanceof MavicDroneWithDropEntity) {
            trySendMavicDropFire(player);
        }
    }

    /** Один запрос сброса на клиентский тик мира — иначе ЛКМ + событие атаки шлют два пакета. */
    private static long wrbdrones$mavicFireCooldownTick = -1L;

    /** Дублирует установку {@code drone.fire} на сервере для Mavic With Drop (режим миномёта не трогаем). */
    private static void trySendMavicDropFire(Player player) {
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.FIRING_PARAMETERS.get()) || off.is(ModItems.ARTILLERY_INDICATOR.get())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long tick = mc.level.getGameTime();
        if (tick == wrbdrones$mavicFireCooldownTick) {
            return;
        }
        wrbdrones$mavicFireCooldownTick = tick;
        PacketDistributor.sendToServer(new MavicDroneFirePacket());
    }

    private static boolean wrbdrones$lancetYawLeft = false;
    private static boolean wrbdrones$lancetYawRight = false;
    private static UUID wrbdrones$lancetInputDrone = null;
    private static UUID wrbdrones$lancetCursorDrone = null;
    private static boolean wrbdrones$hasLancetMouseSample = false;
    private static double wrbdrones$lastLancetMouseX = 0.0;
    private static double wrbdrones$lastLancetMouseY = 0.0;
    private static float wrbdrones$lancetCursorX = 0.0f;
    private static float wrbdrones$lancetCursorY = 0.0f;
    private static final float LANCET_COMMAND_CURSOR_SENSITIVITY = 1.15f;

    private static float freeCameraYaw = 0.0f;
    private static float freeCameraPitch = 0.0f;
    private static boolean wasFreeCamera = false;

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ZalaLancetEntity lancet = getControlledLancet(mc.player);
        
        if (lancet != null) {
            double rawX = mc.mouseHandler.xpos();
            double rawY = mc.mouseHandler.ypos();
            
            if (!lancet.getUUID().equals(wrbdrones$lancetCursorDrone) || !wrbdrones$hasLancetMouseSample) {
                wrbdrones$lancetCursorDrone = lancet.getUUID();
                wrbdrones$hasLancetMouseSample = true;
                wrbdrones$lastLancetMouseX = rawX;
                wrbdrones$lastLancetMouseY = rawY;
                wrbdrones$lancetCursorX = 0.0f;
                wrbdrones$lancetCursorY = 0.0f;
            }
            
            double deltaX = rawX - wrbdrones$lastLancetMouseX;
            double deltaY = rawY - wrbdrones$lastLancetMouseY;
            wrbdrones$lastLancetMouseX = rawX;
            wrbdrones$lastLancetMouseY = rawY;

            if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON && lancet.isFreeCamera()) {
                if (!wasFreeCamera) {
                    // Start at the drone's actual current rotation when entering free camera
                    freeCameraYaw = lancet.getYRot();
                    freeCameraPitch = lancet.getXRot();
                    wasFreeCamera = true;
                }
                
                // Get sensitivity directly from options, avoiding potential 0.0 overrides from other mods
                float sensitivity = (float) mc.options.sensitivity().get().doubleValue() * 0.6f + 0.2f;
                float f1 = sensitivity * sensitivity * sensitivity * 8.0f;
                freeCameraYaw += (float) deltaX * f1 * 0.15f;
                freeCameraPitch += (float) deltaY * f1 * 0.15f;
                freeCameraPitch = Mth.clamp(freeCameraPitch, -90.0f, 90.0f);
            } else {
                wasFreeCamera = false;
                
                if (lancet.getLancetMode() != ZalaLancetEntity.MODE_RECON) {
                    int screenWidth = Math.max(1, mc.getWindow().getScreenWidth());
                    int screenHeight = Math.max(1, mc.getWindow().getScreenHeight());
                    int guiWidth = Math.max(1, mc.getWindow().getGuiScaledWidth());
                    int guiHeight = Math.max(1, mc.getWindow().getGuiScaledHeight());
                    double guiDeltaX = deltaX * (double) guiWidth / (double) screenWidth;
                    double guiDeltaY = deltaY * (double) guiHeight / (double) screenHeight;

                    wrbdrones$lancetCursorX = Mth.clamp(wrbdrones$lancetCursorX
                            + (float) (guiDeltaX / (guiWidth * 0.5) * LANCET_COMMAND_CURSOR_SENSITIVITY), -1.0f, 1.0f);
                    wrbdrones$lancetCursorY = Mth.clamp(wrbdrones$lancetCursorY
                            + (float) (guiDeltaY / (guiHeight * 0.5) * LANCET_COMMAND_CURSOR_SENSITIVITY), -1.0f, 1.0f);
                }
            }
        } else {
            wasFreeCamera = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            flushLancetInput();
            resetLancetCommandCursor();
            return;
        }

        ZalaLancetEntity lancet = getControlledLancet(mc.player);
        if (lancet == null) {
            flushLancetInput();
            resetLancetCommandCursor();
            return;
        }
        updateLancetCommandCursor(mc, lancet);
        suppressLancetVanillaKeys(mc);

        boolean left = LancetKeyMappings.YAW_LEFT.isDown();
        boolean right = LancetKeyMappings.YAW_RIGHT.isDown();
        UUID droneId = lancet.getUUID();
        if (left != wrbdrones$lancetYawLeft || right != wrbdrones$lancetYawRight
                || !droneId.equals(wrbdrones$lancetInputDrone)) {
            PacketDistributor.sendToServer(new LancetInputPacket(droneId, left, right));
            wrbdrones$lancetYawLeft = left;
            wrbdrones$lancetYawRight = right;
            wrbdrones$lancetInputDrone = droneId;
        }

        while (LancetKeyMappings.TARGET.consumeClick()) {
            sendLancetTarget(lancet);
        }

        while (LancetKeyMappings.SWITCH_MODE.consumeClick()) {
            int newMode = lancet.getLancetMode() == ZalaLancetEntity.MODE_COURSE ? ZalaLancetEntity.MODE_RECON : ZalaLancetEntity.MODE_COURSE;
            PacketDistributor.sendToServer(new LancetStatePacket(lancet.getUUID(), newMode, false, -1));
        }

        while (LancetKeyMappings.FREE_CAMERA.consumeClick()) {
            if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON) {
                boolean newCamera = !lancet.isFreeCamera();
                PacketDistributor.sendToServer(new LancetStatePacket(lancet.getUUID(), ZalaLancetEntity.MODE_RECON, newCamera, lancet.getSelectedTargetId()));
            }
        }

        while (LancetKeyMappings.CYCLE_TARGET_LEFT.consumeClick()) {
            cycleTarget(lancet, -1);
        }

        while (LancetKeyMappings.CYCLE_TARGET_RIGHT.consumeClick()) {
            cycleTarget(lancet, 1);
        }

        while (LancetKeyMappings.THERMAL_VISION.consumeClick()) {
            DronePostChainHandler.toggleThermal();
        }
    }

    private static void cycleTarget(ZalaLancetEntity lancet, int direction) {
        if (lancet.getLancetMode() != ZalaLancetEntity.MODE_RECON) {
            return;
        }

        List<Entity> potentialTargets = lancet.level().getEntities(lancet, lancet.getBoundingBox().inflate(150.0), e -> isValidLancetTarget(lancet, e));
        if (potentialTargets.isEmpty()) {
            return;
        }

        potentialTargets.sort(Comparator.comparingInt(Entity::getId));

        int currentId = lancet.getSelectedTargetId();
        int currentIndex = -1;
        for (int i = 0; i < potentialTargets.size(); i++) {
            if (potentialTargets.get(i).getId() == currentId) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex;
        if (currentIndex == -1) {
            nextIndex = direction > 0 ? 0 : potentialTargets.size() - 1;
        } else {
            nextIndex = (currentIndex + direction + potentialTargets.size()) % potentialTargets.size();
        }

        int newId = potentialTargets.get(nextIndex).getId();
        lancet.setSelectedTargetId(newId);
        PacketDistributor.sendToServer(new LancetStatePacket(lancet.getUUID(), ZalaLancetEntity.MODE_RECON, lancet.isFreeCamera(), newId));
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || getControlledLancet(mc.player) == null) {
            return;
        }

        int key = event.getKey();
        int scanCode = event.getScanCode();
        if (LancetKeyMappings.YAW_LEFT.matches(key, scanCode)
                || LancetKeyMappings.YAW_RIGHT.matches(key, scanCode)) {
            suppressLancetVanillaKeys(mc);
        }
    }

    private static void suppressLancetVanillaKeys(Minecraft mc) {
        mc.options.keyDrop.setDown(false);
        mc.options.keyInventory.setDown(false);
        while (mc.options.keyDrop.consumeClick()) {
        }
        while (mc.options.keyInventory.consumeClick()) {
        }
    }

    private static void flushLancetInput() {
        if (wrbdrones$lancetInputDrone != null && (wrbdrones$lancetYawLeft || wrbdrones$lancetYawRight)) {
            PacketDistributor.sendToServer(new LancetInputPacket(wrbdrones$lancetInputDrone, false, false));
        }
        wrbdrones$lancetYawLeft = false;
        wrbdrones$lancetYawRight = false;
        wrbdrones$lancetInputDrone = null;
    }

    private static void resetLancetCommandCursor() {
        wrbdrones$lancetCursorDrone = null;
        wrbdrones$hasLancetMouseSample = false;
        wrbdrones$lancetCursorX = 0.0f;
        wrbdrones$lancetCursorY = 0.0f;
    }

    private static void updateLancetCommandCursor(Minecraft mc, ZalaLancetEntity lancet) {
        // Cursor movement has been moved to onComputeCameraAngles for smooth render-rate updates.
    }

    public static boolean isLancetCommandCursorVisible(ZalaLancetEntity lancet) {
        return lancet != null && lancet.getLancetMode() != ZalaLancetEntity.MODE_RECON && lancet.getUUID().equals(wrbdrones$lancetCursorDrone);
    }

    public static float getLancetCommandCursorX() {
        return wrbdrones$lancetCursorX;
    }

    public static float getLancetCommandCursorY() {
        return wrbdrones$lancetCursorY;
    }

    public static boolean isFreeCameraActive(Player player) {
        ZalaLancetEntity lancet = getControlledLancet(player);
        return lancet != null && lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON && lancet.isFreeCamera();
    }

    public static float getFreeCameraYaw() {
        return freeCameraYaw;
    }

    public static float getFreeCameraPitch() {
        return freeCameraPitch;
    }

    public static ZalaLancetEntity getControlledLancet(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return null;
        }

        var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean("Linked") || !tag.getBoolean("Using")) {
            return null;
        }

        var drone = EntityFindUtil.findDrone(player.level(), tag.getString("LinkedDrone"));
        return drone instanceof ZalaLancetEntity lancet ? lancet : null;
    }

    private static void sendLancetCourseCommand(ZalaLancetEntity lancet) {
        Vec3 worldPoint = computeCourseWorldTarget(lancet);
        if (worldPoint == null) {
            return;
        }
        PacketDistributor.sendToServer(new LancetCourseCommandPacket(
                lancet.getUUID(), worldPoint.x, worldPoint.y, worldPoint.z));
    }

    /**
     * Проецирует текущее положение курсора в точку мира — обратная операция к проекции
     * мировой точки на экран в HUD (тот же FOV и те же экранные коэффициенты), поэтому
     * зафиксированная рамка появляется ровно там, где была «живая» при нажатии ЛКМ.
     */
    private static Vec3 computeCourseWorldTarget(ZalaLancetEntity lancet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) {
            return null;
        }
        var camera = mc.gameRenderer.getMainCamera();
        int sw = Math.max(1, mc.getWindow().getGuiScaledWidth());
        int sh = Math.max(1, mc.getWindow().getGuiScaledHeight());

        double fovY = mc.options.fov().get();
        double fovX = fovY * sw / sh;
        // Экранные коэффициенты те же, что в DroneHudOverlay.commandScreenX/Y (0.45 / 0.40):
        // нормированная экранная координата = cursor * 2 * коэффициент.
        float yawOffset = (float) (wrbdrones$lancetCursorX * 0.90 * (fovX / 2.0));
        float pitchOffset = (float) (wrbdrones$lancetCursorY * 0.80 * (fovY / 2.0));

        float yaw = camera.getYRot() + yawOffset;
        float pitch = camera.getXRot() + pitchOffset;
        Vec3 origin = camera.getPosition();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);

        final double reach = 512.0;
        Vec3 end = origin.add(dir.scale(reach));
        HitResult blockHit = lancet.level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, lancet));
        return blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
    }

    private static void sendLancetTarget(ZalaLancetEntity lancet) {
        final double reach = 512.0;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        var look = camera.getLookVector();
        Vec3 view = new Vec3(look.x(), look.y(), look.z());
        Vec3 end = eye.add(view.scale(reach));

        HitResult blockHit = lancet.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, lancet));
        double maxDistanceSqr = reach * reach;
        double blockDistanceSqr = blockHit.getType() == HitResult.Type.MISS
                ? maxDistanceSqr
                : eye.distanceToSqr(blockHit.getLocation());

        AABB searchBox = lancet.getBoundingBox().expandTowards(view.scale(reach)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(lancet, eye, end, searchBox,
                entity -> isValidLancetTarget(lancet, entity), maxDistanceSqr);

        if (entityHit != null && eye.distanceToSqr(entityHit.getLocation()) <= blockDistanceSqr) {
            Entity target = entityHit.getEntity();
            Vec3 fallback = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
            PacketDistributor.sendToServer(LancetTargetPacket.entity(lancet.getUUID(), target.getUUID(), fallback));
            return;
        }

        if (blockHit.getType() != HitResult.Type.MISS) {
            PacketDistributor.sendToServer(LancetTargetPacket.point(lancet.getUUID(), blockHit.getLocation()));
            return;
        }

        PacketDistributor.sendToServer(LancetTargetPacket.clear(lancet.getUUID()));
    }

    private static boolean isValidLancetTarget(ZalaLancetEntity lancet, Entity entity) {
        return entity != null
                && entity != lancet
                && entity.isAlive()
                && !entity.isSpectator()
                && !(entity instanceof net.minecraft.world.entity.item.ItemEntity)
                && !(entity instanceof net.minecraft.world.entity.projectile.Projectile);
    }
}
