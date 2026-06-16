package ru.liko.wrbdrones.client.sound;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Обработчик звука двигателя для обычных дронов (AddonDroneEntity).
 * Реализует систему, аналогичную Shahed: допплер, затухание по расстоянию и высоте.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DroneSoundHandler {
    private static final Map<UUID, Controller> CONTROLLERS = new HashMap<>();

    private DroneSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.isPaused()) {
            CONTROLLERS.values().forEach(Controller::stopHard);
            CONTROLLERS.clear();
            return;
        }

        final long now = mc.level.getGameTime();

        CONTROLLERS.values().forEach(c -> c.seenThisTick = false);

        for (final var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AddonDroneEntity drone)) {
                continue;
            }
            CONTROLLERS.computeIfAbsent(drone.getUUID(), id -> new Controller())
                    .updateFromEntity(drone, now);
        }

        CONTROLLERS.entrySet().removeIf(e -> e.getValue().shouldRemove(now, e.getKey()));
    }

    private static boolean isControlledByMonitor(AddonDroneEntity drone) {
        var mc = Minecraft.getInstance();
        var listener = mc.player;
        if (listener == null) return false;

        var stack = listener.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) return false;

        var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED)) return false;
        if (!tag.getBoolean("Using")) return false;

        return drone.getStringUUID().equals(tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE));
    }

    private static final class Controller {
        private DroneEngineLoopSoundInstance engine;

        private boolean seenThisTick;
        private boolean destroyed = false;

        private Vec3 prevDronePos = null;

        private void ensureEngine(final Minecraft mc, double maxDistance, final AddonDroneEntity drone) {
            if (engine != null && !engine.isStopped()) {
                return;
            }
            SoundEvent sound = ModSounds.FPV_DRONE_ENGINE.get();
            if (drone instanceof ru.liko.wrbdrones.entity.ZalaLancetEntity) {
                sound = ModSounds.SHAHED136_ENGINE.get();
            }
            engine = new DroneEngineLoopSoundInstance(sound, maxDistance);
            mc.getSoundManager().play(engine);
        }

        private void updateFromEntity(final AddonDroneEntity drone, final long nowTick) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return;
            }

            seenThisTick = true;

            // Дрон уничтожен — обрываем звук мгновенно, без fade и экстраполяции.
            if (drone.isRemoved()) {
                destroyed = true;
                stopHard();
                prevDronePos = null;
                return;
            }

            if (!drone.engineRunning()) {
                if (engine != null) {
                    engine.requestFadeOut();
                }
                prevDronePos = null;
                return;
            }

            // Не воспроизводим внешний звук, если игрок управляет дроном через монитор.
            // Мгновенно обрываем звук: игрок телепортируется к дрону (distance≈0), поэтому любой
            // fade в этот момент играет на максимальной громкости пару секунд — это и есть "баг".
            if (isControlledByMonitor(drone)) {
                stopHard();
                prevDronePos = null;
                return;
            }

            final Vec3 dronePos = drone.position();
            final var motion = drone.getDeltaMovement();
            final double speed = motion.horizontalDistance();

            Vec3 droneVelocity = motion;
            if (prevDronePos != null) {
                droneVelocity = dronePos.subtract(prevDronePos);
            }
            prevDronePos = dronePos;

            // Мощность двигателя и фактор скорости
            final float power = Math.abs(drone.getEntityData().get(VehicleEntity.POWER));
            final float powerMix = Mth.clamp(power / 0.2f, 0.0f, 1.0f);
            final float speedFactor = (float) Mth.clamp(speed / 0.5, 0.0, 1.0);

            Vec3 playerPos = dronePos;
            if (mc.getCameraEntity() != null) {
                playerPos = mc.getCameraEntity().position();
            }
            final double distance = dronePos.distanceTo(playerPos);
            final double maxDist = ServerConfig.FPV_SOUND_MAX_DISTANCE.get();

            final float dopplerPitch = ShahedSoundEffects.computeDopplerPitch(dronePos, droneVelocity, playerPos);
            final float gainHF = ShahedSoundEffects.computeCombinedGainHF(distance, dronePos.y, playerPos.y, maxDist);

            ensureEngine(mc, maxDist, drone);
            if (engine != null) {
                engine.update(
                        dronePos.x, dronePos.y, dronePos.z,
                        powerMix, speedFactor,
                        dopplerPitch, gainHF,
                        nowTick);
            }
        }

        /**
         * Звук мог исчезнуть из {@code entitiesForRendering()} раньше, чем успеет прийти тик с
         * {@link AddonDroneEntity#isRemoved()} — тогда срабатывал плавный фейд. Уничтожение / пропажа
         * сущности из клиентского мира обрабатываем через {@link EntityFindUtil}: сразу {@link #stopHard()}.
         */
        private boolean shouldRemove(final long nowTick, final UUID droneUuid) {
            if (destroyed) {
                stopHard();
                return true;
            }
            if (engine == null || engine.isStopped()) {
                return true;
            }

            if (!seenThisTick) {
                final Minecraft mc = Minecraft.getInstance();
                final Entity entity = mc != null && mc.level != null
                        ? EntityFindUtil.findEntity(mc.level, droneUuid.toString())
                        : null;
                if (entity instanceof AddonDroneEntity drone && !drone.isRemoved()) {
                    updateFromEntity(drone, nowTick);
                    return false;
                }
                stopHard();
                return true;
            }

            return false;
        }

        private void stopHard() {
            if (engine != null) {
                engine.forceStop();
                engine = null;
            }
        }
    }
}
