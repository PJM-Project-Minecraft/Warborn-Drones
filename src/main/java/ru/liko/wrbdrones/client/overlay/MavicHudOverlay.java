package ru.liko.wrbdrones.client.overlay;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
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
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneNoDropEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.network.DroneSignalLostPacket;
import ru.liko.wrbdrones.util.SignalCalculator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.atsuishio.superbwarfare.client.RenderHelper.preciseBlit;

@OnlyIn(Dist.CLIENT)
public class MavicHudOverlay {

    public static final ResourceLocation ID = Wrbdrones.loc("mavic_hud");

    // Текстуры
    private static final ResourceLocation TV_FRAME = Wrbdrones.loc("textures/overlay/tv_frame.png");
    private static final ResourceLocation CROSSHAIR = Wrbdrones.loc("textures/overlay/third_camera.png");

    // Цвета
    private static final int TEXT_COLOR = 0xFFFFFFFF; // Белый
    private static final int SIGNAL_BAR_COLOR = 0xFF00FF00; // Зеленый для полосок сигнала
    private static final int SIGNAL_BAR_EMPTY_COLOR = 0x66FFFFFF; // Полупрозрачный белый для пустых полосок
    private static final int COMPASS_COLOR = 0xFFFFFFFF; // Белый для компаса

    /** Масштаб OSD-шрифта MAX7456. */
    private static final float OSD_SCALE = 0.75f;
    private static final int OSD_LINE = 14;

    private static final Set<UUID> SIGNAL_LOSS_REPORTED = new HashSet<>();

    public static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null)
            return;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON
                || mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;

        if (!firstPerson)
            return;

        ItemStack stack = player.getMainHandItem();
        var stackTag = NBTTool.getTag(stack);

        if (!stack.is(ModItems.MONITOR.get()) || !stackTag.getBoolean("Using")
                || !stackTag.getBoolean("Linked")) {
            return;
        }

        DroneEntity drone = EntityFindUtil.findDrone(player.level(), stackTag.getString("LinkedDrone"));
        if (drone == null)
            return;

        // Проверяем, является ли дрон Mavic
        boolean isMavic = drone instanceof MavicDroneWithDropEntity || drone instanceof MavicDroneNoDropEntity;
        if (!isMavic) {
            return;
        }

        // Проверяем, является ли дрон AddonDroneEntity для проверки РЭБ и расчета
        // дистанции
        AddonDroneEntity addonDrone = null;
        if (drone instanceof AddonDroneEntity) {
            addonDrone = (AddonDroneEntity) drone;
        }

        // Вычисляем данные
        Vec3 operatorPos = player.position();
        // Используем позицию аватара для расчета дистанции, если дрон -
        // AddonDroneEntity
        if (addonDrone != null) {
            Vec3 avatarPos = addonDrone.getOperatorPosition();
            if (avatarPos != null) {
                operatorPos = avatarPos;
            }
        }
        Vec3 dronePos = drone.position();
        double distance = operatorPos.distanceTo(dronePos);
        // Высота - абсолютная координата Y дрона (как в реальных дронах DJI)
        double altitude = drone.getY();
        double speed = drone.getDeltaMovement().length() * 20.0; // м/с (1 блок/тик = 20 м/с)
        float yaw = Mth.lerp(partialTick, drone.yRotO, drone.getYRot());

        // Единая модель сигнала: дистанция + LOS (стены) + высота + РЭБ.
        double maxDistance = ServerConfig.MAVIC_MAX_DISTANCE.get();
        double signalLossDistance = ServerConfig.MAVIC_SIGNAL_LOSS_DISTANCE.get();
        double signalPercent;
        if (addonDrone != null) {
            SignalCalculator.SignalResult sig = SignalCalculator.compute(
                    drone.level(), operatorPos, drone, maxDistance, signalLossDistance);
            signalPercent = sig.finalQuality();
        } else {
            // Не AddonDrone: оставим простой fallback по дистанции.
            if (distance <= signalLossDistance) {
                signalPercent = 1.0;
            } else if (maxDistance > signalLossDistance) {
                double norm = Math.min(1.0, (distance - signalLossDistance) / (maxDistance - signalLossDistance));
                double f = 1.0 - norm;
                signalPercent = f * f;
            } else {
                signalPercent = 0.0;
            }
        }

        // Вычисляем уровень сигнала (0-5)
        int signalLevel = calculateSignalLevel(signalPercent);

        // Проверяем потерю сигнала и отправляем на сервер
        if (addonDrone != null) {
            handleMavicSignalLoss(addonDrone, signalLevel);
        }

        // Настраиваем рендеринг для текстур
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

        // Рисуем TV рамку и прицел
        renderTvFrameAndCrosshair(guiGraphics, screenWidth, screenHeight);

        // Переключаемся на шейдер для линий и текста
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Рисуем новые элементы интерфейса
        renderHomeIndicator(guiGraphics, screenWidth, screenHeight, operatorPos);
        renderInfo(guiGraphics, screenWidth, screenHeight, altitude, distance, speed);
        
        // Рисуем OSD сигнал
        String signalOsdText = getOsdSignal(signalLevel);
        OsdFont.drawString(guiGraphics, signalOsdText, 10, 10, OSD_SCALE, SIGNAL_BAR_COLOR);

        // Рисуем предупреждение о потере сигнала, если сигнал потерян (включая глушение
        // РЭБ)
        if (signalLevel == 0) {
            renderSignalLossWarning(guiGraphics, screenWidth, screenHeight, partialTick);
        }

        // Рендерим помехи (шум) для мавиков - аналогично FPV, но слабее
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        // Восстанавливаем настройки
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Вычисляет уровень сигнала на основе процента сигнала (0-5)
     */
    private static int calculateSignalLevel(double signalPercent) {
        if (signalPercent >= 0.8)
            return 5;
        if (signalPercent >= 0.6)
            return 4;
        if (signalPercent >= 0.4)
            return 3;
        if (signalPercent >= 0.2)
            return 2;
        if (signalPercent > 0.0)
            return 1;
        return 0;
    }

    /**
     * Обрабатывает потерю сигнала для мавика (отправляет пакет на сервер)
     */
    private static void handleMavicSignalLoss(AddonDroneEntity drone, int signalLevel) {
        UUID droneId = drone.getUUID();
        if (signalLevel <= 0) {
            // Ровно 0 уровней — самоуничтожение (взрыв) согласно конфигу.
            if (SIGNAL_LOSS_REPORTED.add(droneId)) {
                boolean destroy = ru.liko.wrbdrones.config.ServerConfig.SIGNAL_DESTROY_ON_ZERO_ENABLED.get();
                PacketDistributor.sendToServer(new DroneSignalLostPacket(droneId, destroy));
            }
        } else if (signalLevel <= 1) {
            if (SIGNAL_LOSS_REPORTED.add(droneId)) {
                PacketDistributor.sendToServer(new DroneSignalLostPacket(droneId, false));
            }
        } else {
            SIGNAL_LOSS_REPORTED.remove(droneId);
        }
    }


    private static String getOsdSignal(int signalLevel) {
        return OsdFont.SIGNAL + " " + (signalLevel * 20) + "%";
    }

    /**
     * Рисует TV рамку и прицел из текстур
     */
    private static void renderTvFrameAndCrosshair(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        // Прицел в центре (текстура)
        guiGraphics.blit(CROSSHAIR, screenWidth / 2 - 16, screenHeight / 2 - 16, 0, 0, 32, 32, 32, 32);

        // TV рамка вокруг экрана
        int addW = (screenWidth / screenHeight) * 48;
        int addH = (screenWidth / screenHeight) * 27;
        // Используем метод из DroneHudOverlay для точного рендеринга
        renderTvFrame(guiGraphics, screenWidth, screenHeight, addW, addH);
    }

    /**
     * Рисует TV рамку с использованием точного блitting
     */
    private static void renderTvFrame(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int addW, int addH) {
        // Используем preciseBlit для точного рендеринга TV рамки (как в
        // DroneHudOverlay)
        preciseBlit(guiGraphics, TV_FRAME, (float) -addW / 2, (float) -addH / 2, 10, 0, 0,
                screenWidth + addW, screenHeight + addH, screenWidth + addW, screenHeight + addH);
    }

    /**
     * Индикатор «домой»: домик-глиф + стрелка, указывающая на оператора (HOME).
     * Направление считается через базис камеры дрона (см. {@link OsdFont#homeArrow}).
     */
    private static void renderHomeIndicator(GuiGraphics guiGraphics, int screenWidth, int screenHeight, Vec3 operatorPos) {
        int centerX = screenWidth / 2;
        int y = screenHeight - 100;

        char arrow = OsdFont.homeArrow(operatorPos);
        String text = OsdFont.HOUSE + " " + arrow;
        OsdFont.drawCentered(guiGraphics, text, centerX, y - OsdFont.height(OSD_SCALE) / 2, OSD_SCALE, COMPASS_COLOR);
    }

    /**
     * Рисует информацию: высота, расстояние, скорость в нижней части экрана
     * (поднято выше)
     */
    private static void renderInfo(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
            double altitude, double distance, double speed) {
        int y = screenHeight - 80; // Поднято выше (было -30)
        int x = 10;
        int lineHeight = OSD_LINE;
        int spacing = 2;

        // Форматируем значения (OSD-шрифт только ASCII, в верхнем регистре)
        String satsText = OsdFont.SAT + " 14";
        String batteryText = OsdFont.BATTERY + " 100%";
        String altitudeText = String.format("ALT: %.1fM", altitude);
        String distanceText = String.format("%c %.1fM", OsdFont.HOME, distance);
        String speedText = String.format("%.0f %c", speed * 3.6, OsdFont.KMH);

        // Рисуем текст OSD-шрифтом
        OsdFont.drawString(guiGraphics, batteryText, x, y - (lineHeight + spacing) * 2, OSD_SCALE, TEXT_COLOR);
        OsdFont.drawString(guiGraphics, satsText, x, y - (lineHeight + spacing), OSD_SCALE, TEXT_COLOR);
        OsdFont.drawString(guiGraphics, altitudeText, x, y, OSD_SCALE, TEXT_COLOR);
        OsdFont.drawString(guiGraphics, distanceText, x, y + lineHeight + spacing, OSD_SCALE, TEXT_COLOR);
        OsdFont.drawString(guiGraphics, speedText, x, y + (lineHeight + spacing) * 2, OSD_SCALE, TEXT_COLOR);
    }

    /**
     * Рисует предупреждение о потере сигнала в стиле DJI
     */
    private static void renderSignalLossWarning(GuiGraphics guiGraphics, int screenWidth, int screenHeight,
            float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        // OSD-шрифт MAX7456 без кириллицы — используем латиницу.
        String warningText = "SIGNAL LOSS";

        // Вычисляем размеры текста OSD-шрифтом
        int textWidth = OsdFont.width(warningText, OSD_SCALE);
        int textHeight = OsdFont.height(OSD_SCALE);

        // Позиция в центре экрана, немного выше центра
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2 - 50;

        // Размеры фона с отступами
        int paddingX = 20;
        int paddingY = 10;
        int bgWidth = textWidth + paddingX * 2;
        int bgHeight = textHeight + paddingY * 2;

        // Анимация пульсации (мигание) для привлечения внимания
        float time = (mc.level != null ? mc.level.getGameTime() : 0) + partialTick;
        float pulse = (float) (0.7f + 0.3f * Math.sin(time * 0.2)); // Пульсация от 0.7 до 1.0

        // Рисуем полупрозрачный черный фон с пульсацией
        int bgAlpha = (int) (0x99 * pulse);
        int bgColor = (bgAlpha << 24) | 0x000000; // Черный фон с пульсирующей прозрачностью

        guiGraphics.fill(centerX - bgWidth / 2, centerY - bgHeight / 2,
                centerX + bgWidth / 2, centerY + bgHeight / 2, bgColor);

        // Рисуем красный текст с пульсацией (контур уже встроен в OSD-глифы)
        int textAlpha = (int) (0xFF * pulse);
        int textColor = (textAlpha << 24) | 0xFF0000; // Красный текст с пульсирующей яркостью
        OsdFont.drawCentered(guiGraphics, warningText, centerX, centerY - textHeight / 2, OSD_SCALE, textColor);
    }
}
