package ru.liko.wrbdrones.client.overlay;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.client.DroneInputHandler;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneNoDropEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;
import ru.liko.wrbdrones.network.DroneSignalLostPacket;
import ru.liko.wrbdrones.util.SignalCalculator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.atsuishio.superbwarfare.entity.vehicle.DroneEntity.CONTROLLER;
import static com.atsuishio.superbwarfare.client.RenderHelper.preciseBlit;

@OnlyIn(Dist.CLIENT)
public final class DroneHudOverlay {

    public static final ResourceLocation ID = Wrbdrones.loc("drone_hud");

    private static final ResourceLocation TV_FRAME = Wrbdrones.loc("textures/overlay/tv_frame.png");
    private static final ResourceLocation CROSSHAIR = Wrbdrones.loc("textures/overlay/third_camera.png");

    private static final int BLACK_BAR_COLOR = 0xFF000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TARGETING_BRACKET_COLOR = 0xFFFFFFFF;

    /** Масштаб OSD-шрифта (глиф 12×18 px → ~9×13 на экране). */
    private static final float OSD_SCALE = 0.75f;
    /** Межстрочный интервал телеметрии при {@link #OSD_SCALE}. */
    private static final int OSD_LINE = 14;

    private static final Set<UUID> SIGNAL_LOSS_REPORTED = new HashSet<>();

    public static void render(GuiGraphics gfx, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null)
            return;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        CameraType camera = mc.options.getCameraType();
        if (!(camera == CameraType.FIRST_PERSON || camera == CameraType.THIRD_PERSON_BACK))
            return;

        ItemStack monitor = player.getMainHandItem();
        if (!monitor.is(ModItems.MONITOR.get()))
            return;
        var monitorTag = NBTTool.getTag(monitor);
        if (!monitorTag.getBoolean("Using"))
            return;
        if (!monitorTag.getBoolean("Linked"))
            return;

        DroneEntity drone = EntityFindUtil.findDrone(player.level(), monitorTag.getString("LinkedDrone"));
        if (!(drone instanceof AddonDroneEntity addonDrone))
            return;
        
        if (drone.isRemoved())
            return;
            
        if (addonDrone instanceof MavicDroneWithDropEntity || addonDrone instanceof MavicDroneNoDropEntity)
            return;

        int square = Math.min(screenWidth, screenHeight);
        float visibleRatio = (addonDrone instanceof ZalaLancetEntity) ? 0.80f : 0.40f;
        int visibleWidth = (int) (square * visibleRatio);
        int sideBarWidth = (screenWidth - visibleWidth) / 2;
        sideBarWidth = (int) (sideBarWidth * 0.65f * 0.65f);
        sideBarWidth = Math.max(sideBarWidth, (addonDrone instanceof ZalaLancetEntity) ? 20 : 80);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        Vec3 operatorPos = player.position();
        Vec3 avatarPos = addonDrone.getOperatorPosition();
        if (avatarPos != null) {
            operatorPos = avatarPos;
        }
        double distance = operatorPos.distanceTo(drone.position());

        double maxDistance = getMaxSignalDistance(addonDrone);
        SignalCalculator.SignalResult sig = SignalCalculator.compute(
                drone.level(), operatorPos, drone, maxDistance, -1.0);
        double signalStrength = sig.finalQuality();

        int signal = (int) Math.max(0, Math.min(99, signalStrength * 100));

        // 1. Рендерим боковые полосы для всех дронов (создает единый стиль соотношения сторон)
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        renderSideBars(gfx, screenWidth, screenHeight, sideBarWidth);

        // 2. Рендерим ТВ-рамку поверх
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        renderTVFrame(gfx, screenWidth, screenHeight);

        // 3. Специфичная отрисовка интерфейса
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        if (addonDrone instanceof ZalaLancetEntity lancet) {
            renderLancetHud(gfx, screenWidth, screenHeight, sideBarWidth, lancet, distance, signal);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            renderFPVCrosshair(gfx, screenWidth, screenHeight);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            renderTargetingBrackets(gfx, screenWidth, screenHeight, sideBarWidth);
            renderIndicators(gfx, screenWidth, screenHeight, sideBarWidth, addonDrone, player, distance, monitor, signal);
        }

        // 4. Эффект потери сигнала
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        handleSignalLoss(addonDrone, signal);

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void handleSignalLoss(AddonDroneEntity drone, int signal) {
        UUID droneId = drone.getUUID();
        if (signal <= 0) {
            if (SIGNAL_LOSS_REPORTED.add(droneId)) {
                boolean destroy = ServerConfig.SIGNAL_DESTROY_ON_ZERO_ENABLED.get();
                PacketDistributor.sendToServer(new DroneSignalLostPacket(droneId, destroy));
            }
        } else if (signal <= 1) {
            if (SIGNAL_LOSS_REPORTED.add(droneId)) {
                PacketDistributor.sendToServer(new DroneSignalLostPacket(droneId, false));
            }
        } else {
            SIGNAL_LOSS_REPORTED.remove(droneId);
        }
    }

    private static double getMaxSignalDistance(AddonDroneEntity drone) {
        if (drone instanceof ZalaLancetEntity) {
            return ServerConfig.LANCET_MAX_DISTANCE.get();
        }
        return ServerConfig.FPV_MAX_DISTANCE.get();
    }

    private static void renderLancetHud(GuiGraphics gfx, int screenWidth, int screenHeight, int sideBarWidth, ZalaLancetEntity lancet, double distance, int signal) {
        Minecraft mc = Minecraft.getInstance();

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        // Цветовая палитра военного HUD
        int normalColor = 0xDDFFFFFF;
        int attackColor = 0xFFFF3030;
        int trackColor = 0xFF40FF40; // Зеленый для захвата
        
        int statusColor = lancet.isTerminalAttack() ? attackColor : (lancet.hasTarget() ? trackColor : normalColor);

        // 1. Статичное тактическое перекрестие в центре
        drawMilitaryCrosshair(gfx, centerX, centerY, normalColor);

        // Крупная квадратная рамка как на OSD ZALA Lancet
        // В оригинале размер рамки довольно компактный
        int frameSize = Math.max(80, Math.min(screenWidth, screenHeight) / 6);
        int bracketW = frameSize;
        int bracketH = frameSize;

        // 2. Плавающая рамка (текущее положение курсора мыши)
        boolean liveFrame = DroneInputHandler.isLancetCommandCursorVisible(lancet);
        if (liveFrame) {
            float liveX = DroneInputHandler.getLancetCommandCursorX();
            float liveY = DroneInputHandler.getLancetCommandCursorY();
            int liveDrawX = commandScreenX(centerX, screenWidth, liveX);
            int liveDrawY = commandScreenY(centerY, screenHeight, liveY);
            
            renderCornerBrackets(gfx, liveDrawX, liveDrawY, bracketW, bracketH, normalColor);
        }
        
        // 3. Зафиксированная рамка: курс — это точка в мире, проецируем её обратно на экран.
        // Маркер стоит ровно на той точке, куда целился игрок, и плавно идёт к центру по мере
        // того, как нос дрона доворачивается на курс — никаких «перепрыгиваний».
        if (lancet.hasCourseCommand()) {
            int[] cmd = projectWorldToScreen(mc, lancet.getCourseTargetPos(), screenWidth, screenHeight, centerX, centerY);
            if (cmd != null) {
                int boxSize = Math.max(80, Math.min(screenWidth, screenHeight) / 6);
                renderLancetTargetBox(gfx, cmd[0], cmd[1], boxSize, statusColor);
            }
        } else if (lancet.hasTarget()) {
            int boxSize = Math.max(80, Math.min(screenWidth, screenHeight) / 6);
            renderLancetTargetBox(gfx, centerX, centerY, boxSize, trackColor);
        }

        // 4. Круговой компас вокруг центра
        float lookYaw = mc.gameRenderer.getMainCamera().getYRot();
        int compassRadius = Math.max(80, Math.min(screenWidth, screenHeight) / 6) / 2 + 35;
        renderCompass(gfx, mc.font, centerX, centerY, compassRadius, lookYaw);

        // 3. Телеметрия по углам видимой зоны
        int padding = 15;
        int leftX = sideBarWidth + padding;
        int rightX = screenWidth - sideBarWidth - padding;
        int topY = padding + 10;
        int bottomY = screenHeight - padding - 25;

        // --- Верхний Левый: Производитель и Режим ---
        OsdFont.drawString(gfx, "ZALA AERO GROUP", leftX, topY, OSD_SCALE, normalColor);

        String modeText;
        if (lancet.isTerminalAttack()) {
            modeText = "MODE: ATTACK";
        } else if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON) {
            modeText = lancet.isFreeCamera() ? "MODE: RECON (FREE CAM)" : "MODE: RECON";
        } else if (lancet.hasTarget()) {
            modeText = "MODE: TRACKING";
        } else {
            modeText = "MODE: COURSE";
        }
        OsdFont.drawString(gfx, modeText, leftX, topY + 2 * OSD_LINE, OSD_SCALE, statusColor);

        // Display selected target in the telemetry corner
        if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON && lancet.getSelectedTargetId() != -1 && mc.level != null) {
            net.minecraft.world.entity.Entity target = mc.level.getEntity(lancet.getSelectedTargetId());
            if (target != null && target.isAlive()) {
                // Имя сущности может содержать кириллицу/строчные — рендерим ванильным шрифтом.
                String targetName = "TGT: " + target.getDisplayName().getString().toUpperCase();
                drawOutlinedString(gfx, mc.font, Component.literal(targetName), leftX, topY + 2 * OSD_LINE, attackColor, false);
            }
        }

        // Отрисовка цели в режиме разведки
        if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON) {
            int targetId = lancet.getSelectedTargetId();
            if (targetId != -1 && mc.level != null) {
                net.minecraft.world.entity.Entity target = mc.level.getEntity(targetId);
                if (target != null && target.isAlive()) {
                    Vec3 worldPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    int[] s = projectWorldToScreen(mc, worldPos, screenWidth, screenHeight, centerX, centerY);
                    if (s != null && s[0] > 0 && s[0] < screenWidth && s[1] > 0 && s[1] < screenHeight) {
                        renderCornerBrackets(gfx, s[0], s[1], 40, 40, attackColor);
                        String tName = target.getDisplayName().getString();
                        drawOutlinedString(gfx, mc.font, Component.literal(tName), s[0], s[1] + 25, attackColor, true);
                    }
                }
            }
        }

        // --- Верхний Правый: Скорость и Газ ---
        String speedText = String.format("%.0f %c", lancet.getAirspeed() * 72.0f, OsdFont.KMH);
        String throttleText = String.format("THR: %03d%%", Math.round(Mth.clamp(lancet.getThrottle(), 0.0f, 1.0f) * 100.0f));
        OsdFont.drawRight(gfx, speedText, rightX, topY, OSD_SCALE, normalColor);
        OsdFont.drawRight(gfx, throttleText, rightX, topY + OSD_LINE, OSD_SCALE, normalColor);

        OsdFont.drawRight(gfx, OsdFont.BATTERY + " 100%", rightX, topY + 5 * OSD_LINE, OSD_SCALE, normalColor);

        // Полоса газа под текстом
        int barW = 50;
        int barH = 3;
        int barX = rightX - barW;
        int barY = topY + 3 * OSD_LINE;
        int fillW = Math.round(barW * Mth.clamp(lancet.getThrottle(), 0.0f, 1.0f));
        gfx.fill(barX, barY, barX + barW, barY + barH, 0x88000000);
        gfx.fill(barX, barY, barX + fillW, barY + barH, normalColor);

        // --- Нижний Левый: Дистанция ---
        String distanceText = String.format("%c %.1fM", OsdFont.HOME, distance);
        OsdFont.drawString(gfx, distanceText, leftX, bottomY, OSD_SCALE, normalColor);

        // --- Нижний Центр: Направление домой ---
        Vec3 operatorPos = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.position() : lancet.position();
        if (lancet.getOperatorPosition() != null) operatorPos = lancet.getOperatorPosition();
        String arrow = String.valueOf(OsdFont.homeArrow(operatorPos));
        OsdFont.drawCentered(gfx, OsdFont.HOME + " " + arrow, centerX, bottomY, OSD_SCALE * 1.5f, normalColor);

        // --- Нижний Правый: Сигнал и таймер ---
        int seconds = (int) (lancet.tickCount / 20);
        int mins = seconds / 60;
        int secs = seconds % 60;
        String timerText = String.format("FLY %02d:%02d", mins, secs);
        OsdFont.drawRight(gfx, timerText, rightX, bottomY - OSD_LINE, OSD_SCALE, normalColor);

        String signalText = String.format("%c %02d", OsdFont.SIGNAL, signal);
        OsdFont.drawRight(gfx, signalText, rightX, bottomY, OSD_SCALE, normalColor);
    }



    /** Толщина белой линии L-уголков (как у штрихов глифов), px. */
    private static final int RETICLE_THICK = 2;
    /** Цвет чёрного контура линий рамки — повторяет окантовку глифов. */
    private static final int RETICLE_OUTLINE = 0xFF000000;
    /** Масштаб центрального «+» (глиф 0x0B). */
    private static final float CROSS_SCALE = 1.0f;

    /** Центральный «+» глифом 0x2B (+). В оригинале Ланцета перекрестие очень компактное. */
    private static void drawCrossGlyph(GuiGraphics gfx, int cx, int cy, float scale, int color) {
        int y = cy - Math.round(OsdFont.GLYPH_H * scale) / 2;
        OsdFont.drawCentered(gfx, "+", cx, y, scale, color); // 0x2B is '+'
    }

    private static void drawMilitaryCrosshair(GuiGraphics gfx, int centerX, int centerY, int color) {
        drawCrossGlyph(gfx, centerX, centerY, CROSS_SCALE, color);
    }

    /**
     * Рисует Г-образный угол рамки: горизонтальный сегмент длиной {@code hx} и
     * вертикальный длиной {@code vy} из точки (x, y). Линии белые с чёрным контуром,
     * идеально состыкованы в углу для ровного отображения под глифы OSD.
     */
    private static void drawBracketL(GuiGraphics gfx, int x, int y, int hx, int vy, int color) {
        int t = RETICLE_THICK;
        int h = t / 2;
        
        int hx1 = hx > 0 ? x - h : x + hx;
        int hx2 = hx > 0 ? x + hx : x + h;
        int hy1 = y - h;
        int hy2 = y + h;
        
        int vx1 = x - h;
        int vx2 = x + h;
        int vy1 = vy > 0 ? y - h : y + vy;
        int vy2 = vy > 0 ? y + vy : y + h;

        // Чёрный контур обоих сегментов (на 1 px шире со всех сторон)
        gfx.fill(hx1 - 1, hy1 - 1, hx2 + 1, hy2 + 1, RETICLE_OUTLINE);
        gfx.fill(vx1 - 1, vy1 - 1, vx2 + 1, vy2 + 1, RETICLE_OUTLINE);
        
        // Белая заливка поверх контура
        gfx.fill(hx1, hy1, hx2, hy2, color);
        gfx.fill(vx1, vy1, vx2, vy2, color);
    }

    /**
     * Проецирует точку мира на экран через текущую камеру (yaw/pitch + FOV).
     * Возвращает {@code null}, если точка позади камеры. Обратная операция к
     * {@link ru.liko.wrbdrones.client.DroneInputHandler#computeCourseWorldTarget}.
     */
    private static int[] projectWorldToScreen(Minecraft mc, Vec3 worldPos, int screenWidth, int screenHeight,
            int centerX, int centerY) {
        if (mc.gameRenderer == null) {
            return null;
        }
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 to = worldPos.subtract(camera.getPosition());
        if (to.lengthSqr() <= 1.0e-4) {
            return null;
        }
        Vec3 dir = to.normalize();
        org.joml.Vector3f lookVec = camera.getLookVector();
        Vec3 look = new Vec3(lookVec.x(), lookVec.y(), lookVec.z());
        if (dir.dot(look) <= 0.0) {
            return null;
        }

        float targetYaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0f;
        float targetPitch = (float) -(Mth.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * Mth.RAD_TO_DEG);
        float yawDiff = Mth.wrapDegrees(targetYaw - camera.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - camera.getXRot());

        double fovY = mc.options.fov().get();
        double fovX = fovY * screenWidth / (double) screenHeight;
        int x = centerX + (int) ((yawDiff / (fovX / 2.0)) * (screenWidth / 2.0));
        int y = centerY + (int) ((pitchDiff / (fovY / 2.0)) * (screenHeight / 2.0));
        return new int[]{x, y};
    }

    private static int commandScreenX(int centerX, int screenWidth, float normalizedX) {
        return centerX + Math.round(Mth.clamp(normalizedX, -1.0f, 1.0f) * screenWidth * 0.45f);
    }

    private static int commandScreenY(int centerY, int screenHeight, float normalizedY) {
        return centerY + Math.round(Mth.clamp(normalizedY, -1.0f, 1.0f) * screenHeight * 0.40f);
    }

    /** Рамка из 4 угловых L-скобок (как на OSD ZALA Lancet). */
    private static void renderCornerBrackets(GuiGraphics gfx, int centerX, int centerY, int width, int height, int color) {
        int left = centerX - width / 2;
        int right = centerX + width / 2;
        int top = centerY - height / 2;
        int bottom = centerY + height / 2;
        int arm = Math.max(6, Math.min(width, height) / 5);

        drawBracketL(gfx, left, top, arm, arm, color);        // верх-лево
        drawBracketL(gfx, right, top, -arm, arm, color);      // верх-право
        drawBracketL(gfx, left, bottom, arm, -arm, color);    // низ-лево
        drawBracketL(gfx, right, bottom, -arm, -arm, color);  // низ-право
    }

    /** Целеуказательная рамка: те же 4 угла + «+» глифом по центру точки. */
    private static void renderLancetTargetBox(GuiGraphics gfx, int centerX, int centerY, int size, int color) {
        renderCornerBrackets(gfx, centerX, centerY, size, size, color);
        drawCrossGlyph(gfx, centerX, centerY, 0.75f, color);
    }

    private static void renderCompass(GuiGraphics gfx, net.minecraft.client.gui.Font font, int centerX, int centerY, int radius, float lookYaw) {
        int color = 0xFF66CCFF; // Светло-голубой цвет как на реальном HUD ZALA
        drawCompassLetter(gfx, "S", 0 - lookYaw, centerX, centerY, radius, color);
        drawCompassLetter(gfx, "W", 90 - lookYaw, centerX, centerY, radius, color);
        drawCompassLetter(gfx, "N", 180 - lookYaw, centerX, centerY, radius, color);
        drawCompassLetter(gfx, "E", 270 - lookYaw, centerX, centerY, radius, color);
    }

    private static void drawCompassLetter(GuiGraphics gfx, String letter, float angleDeg, int centerX, int centerY, int radius, int color) {
        double rad = Math.toRadians(angleDeg);
        int x = centerX + (int)(Math.sin(rad) * radius);
        int y = centerY - (int)(Math.cos(rad) * radius);
        OsdFont.drawCentered(gfx, letter, x, y - OsdFont.height(OSD_SCALE) / 2, OSD_SCALE, color);
    }



    private static void renderTVFrame(GuiGraphics gfx, int screenWidth, int screenHeight) {
        float addW = (float) screenWidth / screenHeight * 48f;
        float addH = (float) screenWidth / screenHeight * 27f;
        preciseBlit(gfx, TV_FRAME, -addW / 2f, -addH / 2f, 10, 0, 0,
                screenWidth + addW, screenHeight + addH, screenWidth + addW, screenHeight + addH);
    }

    private static void renderFPVCrosshair(GuiGraphics gfx, int screenWidth, int screenHeight) {
        gfx.blit(CROSSHAIR, screenWidth / 2 - 16, screenHeight / 2 - 16, 0, 0, 32, 32, 32, 32);
    }

    private static void renderSideBars(GuiGraphics gfx, int screenWidth, int screenHeight, int sideBarWidth) {
        if (sideBarWidth <= 0)
            return;
        gfx.fill(0, 0, sideBarWidth, screenHeight, BLACK_BAR_COLOR);
        gfx.fill(screenWidth - sideBarWidth, 0, screenWidth, screenHeight, BLACK_BAR_COLOR);
    }

    private static void renderTargetingBrackets(GuiGraphics gfx, int screenWidth, int screenHeight, int sideBarWidth) {
        int visibleWidth = screenWidth - 2 * sideBarWidth;
        int centerX = screenWidth / 2;
        int bracketWidth = (int) (visibleWidth / 5.94f);
        int bracketHeight = screenHeight / 3;
        int top = screenHeight / 2 - bracketHeight / 2;
        int bottom = screenHeight / 2 + bracketHeight / 2;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = gfx.pose().last().pose();

        drawDashedLine(buffer, matrix, centerX - bracketWidth, top, centerX - bracketWidth, bottom, 8, 4, TARGETING_BRACKET_COLOR);
        drawDashedLine(buffer, matrix, centerX + bracketWidth, top, centerX + bracketWidth, bottom, 8, 4, TARGETING_BRACKET_COLOR);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void drawDashedLine(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, int dashLength, int dashGap, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0)
            return;

        float step = dashLength + dashGap;
        int segments = (int) (length / step) + 1;

        for (int i = 0; i < segments; i++) {
            float start = (i * step) / length;
            float end = Math.min(1.0f, (i * step + dashLength) / length);
            float sx = x1 + dx * start;
            float sy = y1 + dy * start;
            float ex = x1 + dx * end;
            float ey = y1 + dy * end;
            buffer.addVertex(matrix, sx, sy, 0).setColor(color);
            buffer.addVertex(matrix, ex, ey, 0).setColor(color);
        }
    }

    private static void renderIndicators(GuiGraphics gfx, int screenWidth, int screenHeight, int sideBarWidth, AddonDroneEntity drone, Player player, double distance, ItemStack monitor, int signal) {
        Minecraft mc = Minecraft.getInstance();

        String controllerUuid = drone.getEntityData().get(CONTROLLER);
        Player controller = null;
        if (controllerUuid != null && !controllerUuid.isEmpty() && !"undefined".equals(controllerUuid)
                && !"none".equals(controllerUuid)) {
            controller = EntityFindUtil.findPlayer(drone.level(), controllerUuid);
        }
        if (controller == null) {
            String linked = NBTTool.getTag(monitor).getString("LinkedDrone");
            if (drone.getStringUUID().equals(linked))
                controller = player;
        }

        String callsign = (controller != null ? controller.getDisplayName().getString() : "CROCUS").toUpperCase();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        int padding = 15;
        int leftX = sideBarWidth + padding;
        int rightX = screenWidth - sideBarWidth - padding;
        int topY = padding + 10;
        int bottomY = screenHeight - padding - 25;

        // Рисуем позывной по центру
        OsdFont.drawCentered(gfx, callsign, centerX, centerY - 60, OSD_SCALE, TEXT_COLOR);

        // --- Верхний Левый: Пусто ---

        // --- Верхний Правый: Сигнал и Батарея ---
        String signalText = String.format("%c %02d", OsdFont.SIGNAL, signal);
        OsdFont.drawRight(gfx, signalText, rightX, topY, OSD_SCALE, TEXT_COLOR);
        OsdFont.drawRight(gfx, OsdFont.BATTERY + " 100%", rightX, topY + OSD_LINE, OSD_SCALE, TEXT_COLOR);

        // Определяем позицию оператора
        Vec3 operatorPos = player.position();
        if (drone.getOperatorPosition() != null) operatorPos = drone.getOperatorPosition();

        // --- Нижний Левый: Телеметрия (Высота, Скорость) ---
        double altitude = drone.getY() - operatorPos.y;
        double speed = drone.getDeltaMovement().horizontalDistance() * 72.0;
        OsdFont.drawString(gfx, String.format("ALT: %.1f M", altitude), leftX, bottomY - OSD_LINE, OSD_SCALE, TEXT_COLOR);
        OsdFont.drawString(gfx, String.format("%.0f %c", speed, OsdFont.KMH), leftX, bottomY, OSD_SCALE, TEXT_COLOR);

        // --- Нижний Центр: Режим полета и Направление домой ---
        String mode = "ACRO";
        OsdFont.drawCentered(gfx, mode, centerX, bottomY, OSD_SCALE, TEXT_COLOR);
        
        String arrow = String.valueOf(OsdFont.homeArrow(operatorPos));
        // Большая стрелка (увеличенный масштаб)
        OsdFont.drawCentered(gfx, arrow, centerX, bottomY - OSD_LINE, OSD_SCALE * 1.5f, TEXT_COLOR);

        float pitch = drone.getXRot();
        // Искусственный горизонт: при взгляде вниз (pitch > 0), горизонт уходит вверх (-pitch)
        int pitchOffset = (int) (-pitch * 1.5f);
        int gap = 40;
        OsdFont.drawRight(gfx, "---", centerX - gap, centerY + pitchOffset, OSD_SCALE, TEXT_COLOR);
        OsdFont.drawString(gfx, "---", centerX + gap, centerY + pitchOffset, OSD_SCALE, TEXT_COLOR);

        // --- Нижний Правый: Дистанция и Таймер ---
        int seconds = (int) (drone.tickCount / 20);
        int mins = seconds / 60;
        int secs = seconds % 60;
        String timerText = String.format("FLY %02d:%02d", mins, secs);
        OsdFont.drawRight(gfx, timerText, rightX, bottomY - OSD_LINE, OSD_SCALE, TEXT_COLOR);
        
        String distText = String.format("%c %.1f M", OsdFont.HOME, distance);
        OsdFont.drawRight(gfx, distText, rightX, bottomY, OSD_SCALE, TEXT_COLOR);
    }

    private static void drawOutlinedString(GuiGraphics gfx, net.minecraft.client.gui.Font font, Component text, int x, int y, int color, boolean centered) {
        int width = font.width(text);
        int drawX = centered ? x - width / 2 : x;

        int outlineColor = 0xFF000000;
        gfx.drawString(font, text, drawX - 1, y, outlineColor, false);
        gfx.drawString(font, text, drawX + 1, y, outlineColor, false);
        gfx.drawString(font, text, drawX, y - 1, outlineColor, false);
        gfx.drawString(font, text, drawX, y + 1, outlineColor, false);

        gfx.drawString(font, text, drawX, y, color, false);
    }


} // <-- Эта скобка закрывает сам класс DroneHudOverlay (обязательна!)