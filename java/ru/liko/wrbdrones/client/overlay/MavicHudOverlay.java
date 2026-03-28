package ru.liko.wrbdrones.client.overlay;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneNoDropEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.network.ModNetworking;
import ru.liko.wrbdrones.util.RebUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.atsuishio.superbwarfare.client.RenderHelper.preciseBlit;

@OnlyIn(Dist.CLIENT)
public class MavicHudOverlay implements IGuiOverlay {

    public static final String ID = Wrbdrones.MODID + "_mavic_hud";

    // Текстуры
    private static final ResourceLocation TV_FRAME = Wrbdrones.id("textures/overlay/tv_frame.png");
    private static final ResourceLocation CROSSHAIR = Wrbdrones.id("textures/overlay/third_camera.png");

    // Цвета
    private static final int TEXT_COLOR = 0xFFFFFFFF; // Белый
    private static final int SIGNAL_BAR_COLOR = 0xFF00FF00; // Зеленый для полосок сигнала
    private static final int SIGNAL_BAR_EMPTY_COLOR = 0x66FFFFFF; // Полупрозрачный белый для пустых полосок
    private static final int COMPASS_COLOR = 0xFFFFFFFF; // Белый для компаса

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = gui.getMinecraft();
        Player player = mc.player;

        if (player == null) return;

        boolean firstPerson = Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON 
                || Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_BACK;

        if (!firstPerson) return;

        ItemStack stack = player.getMainHandItem();

        if (!stack.is(ModItems.MONITOR.get()) || !stack.getOrCreateTag().getBoolean("Using") 
                || !stack.getOrCreateTag().getBoolean("Linked")) {
            return;
        }

        DroneEntity drone = EntityFindUtil.findDrone(player.level(), stack.getOrCreateTag().getString("LinkedDrone"));
        if (drone == null) return;
        
        // Проверяем, является ли дрон Mavic
        boolean isMavic = drone instanceof MavicDroneWithDropEntity || drone instanceof MavicDroneNoDropEntity;
        if (!isMavic) {
            return;
        }

        // Проверяем, является ли дрон AddonDroneEntity для проверки РЭБ и расчета дистанции
        AddonDroneEntity addonDrone = null;
        if (drone instanceof AddonDroneEntity) {
            addonDrone = (AddonDroneEntity) drone;
        }
        
        // Вычисляем данные
        Vec3 operatorPos = player.position();
        // Используем позицию аватара для расчета дистанции, если дрон - AddonDroneEntity
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
        
        // Получаем коэффициент глушения сигнала от РЭБ
        double rebJammingFactor = addonDrone != null ? getRebJammingFactor(addonDrone) : 0.0;
        
        // Вычисляем уровень сигнала на основе расстояния
        double maxDistance = ServerConfig.MAVIC_MAX_DISTANCE.get();
        double signalLossDistance = ServerConfig.MAVIC_SIGNAL_LOSS_DISTANCE.get();
        
        // Вычисляем процент сигнала
        double signalPercent = 1.0;
        if (distance > signalLossDistance && maxDistance > signalLossDistance) {
            double norm = Math.min(1.0, (distance - signalLossDistance) / (maxDistance - signalLossDistance));
            signalPercent = 1.0 - norm;
            signalPercent = signalPercent * signalPercent; // Квадратичная функция
        }
        
        // Применяем глушение от РЭБ
        signalPercent = signalPercent * (1.0 - rebJammingFactor);
        
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
                GlStateManager.DestFactor.ZERO
        );
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Рисуем TV рамку и прицел
        renderTvFrameAndCrosshair(guiGraphics, screenWidth, screenHeight);

        // Переключаемся на шейдер для линий и текста
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // Рисуем новые элементы интерфейса
        renderSignalBars(guiGraphics, screenWidth, screenHeight, signalLevel);
        renderCompass(guiGraphics, screenWidth, screenHeight, yaw);
        renderInfo(guiGraphics, screenWidth, screenHeight, altitude, distance, speed);
        
        // Рисуем предупреждение о потере сигнала, если сигнал потерян (включая глушение РЭБ)
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
    private int calculateSignalLevel(double signalPercent) {
        if (signalPercent >= 0.8) return 5;
        if (signalPercent >= 0.6) return 4;
        if (signalPercent >= 0.4) return 3;
        if (signalPercent >= 0.2) return 2;
        if (signalPercent > 0.0) return 1;
        return 0;
    }
    
    /**
     * Обрабатывает потерю сигнала для мавика (отправляет пакет на сервер)
     */
    private void handleMavicSignalLoss(AddonDroneEntity drone, int signalLevel) {
        UUID droneId = drone.getUUID();
        if (signalLevel == 0) {
            if (SIGNAL_LOSS_REPORTED.add(droneId)) {
                ModNetworking.reportSignalLost(droneId);
            }
        } else {
            SIGNAL_LOSS_REPORTED.remove(droneId);
        }
    }
    
    /**
     * Возвращает коэффициент глушения сигнала от РЭБ.
     */
    private double getRebJammingFactor(AddonDroneEntity drone) {
        return RebUtils.getRebFactor(drone);
    }
    
    private static final Set<UUID> SIGNAL_LOSS_REPORTED = new HashSet<>();
    
    /**
     * Рисует индикатор уровня сигнала (Wi-Fi стиль) в левом верхнем углу
     */
    private void renderSignalBars(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int signalLevel) {
        int x = 10;
        int y = 10;
        int barWidth = 4;
        int barSpacing = 2;
        int maxBars = 5;
        
        for (int i = 0; i < maxBars; i++) {
            int barHeight = 3 + i * 2; // Высота увеличивается для каждой полоски
            int barX = x + i * (barWidth + barSpacing);
            int barY = y + (maxBars - i - 1) * 2; // Выравнивание по нижнему краю
            
            int color = (i < signalLevel) ? SIGNAL_BAR_COLOR : SIGNAL_BAR_EMPTY_COLOR;
            guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, color);
        }
    }

    /**
     * Рисует TV рамку и прицел из текстур
     */
    private void renderTvFrameAndCrosshair(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
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
    private void renderTvFrame(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int addW, int addH) {
        // Используем preciseBlit для точного рендеринга TV рамки (как в DroneHudOverlay)
        preciseBlit(guiGraphics, TV_FRAME, (float) -addW / 2, (float) -addH / 2, 10, 0, 0, 
                screenWidth + addW, screenHeight + addH, screenWidth + addW, screenHeight + addH);
    }

    /**
     * Рисует индикатор направления (стрелка на север) в нижней части экрана
     */
    private void renderCompass(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float yaw) {
        int centerX = screenWidth / 2;
        int y = screenHeight - 100; // Перемещено вниз
        int compassRadius = 50;
        
        // Нормализуем yaw к диапазону 0-360
        float normalizedYaw = (yaw % 360 + 360) % 360;
        
        // Вычисляем направление на север (0 градусов = север)
        float northOffset = -normalizedYaw; // Инвертируем, так как yaw увеличивается по часовой стрелке
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, y, 0);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(northOffset));
        guiGraphics.pose().translate(-centerX, -y, 0);

        // Рисуем стрелку на север
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        Matrix4f matrix = guiGraphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // Стрелка (треугольник, указывающий на север)
        float arrowSize = 8;
        buffer.vertex(matrix, centerX, y - compassRadius, 0).color(COMPASS_COLOR).endVertex();
        buffer.vertex(matrix, centerX - arrowSize, y - compassRadius + arrowSize * 2, 0).color(COMPASS_COLOR).endVertex();
        buffer.vertex(matrix, centerX + arrowSize, y - compassRadius + arrowSize * 2, 0).color(COMPASS_COLOR).endVertex();

        BufferUploader.drawWithShader(buffer.end());

        // Рисуем букву "N" для севера
        Minecraft mc = Minecraft.getInstance();
        String northLabel = "N";
        int labelWidth = mc.font.width(northLabel);
        guiGraphics.drawString(mc.font, Component.literal(northLabel), 
                              centerX - labelWidth / 2, y - compassRadius - 12, COMPASS_COLOR, false);

        guiGraphics.pose().popPose();
    }

    /**
     * Рисует информацию: высота, расстояние, скорость в нижней части экрана (поднято выше)
     */
    private void renderInfo(GuiGraphics guiGraphics, int screenWidth, int screenHeight, 
                           double altitude, double distance, double speed) {
        Minecraft mc = Minecraft.getInstance();
        int y = screenHeight - 80; // Поднято выше (было -30)
        int x = 10;
        int lineHeight = 12;
        int spacing = 2;

        // Форматируем значения
        String altitudeText = String.format("H: %.1fm", altitude);
        String distanceText = String.format("D: %.1fm", distance);
        String speedText = String.format("S: %.1fm/s", speed);

        // Рисуем текст
        guiGraphics.drawString(mc.font, Component.literal(altitudeText), x, y, TEXT_COLOR, false);
        guiGraphics.drawString(mc.font, Component.literal(distanceText), x, y + lineHeight + spacing, TEXT_COLOR, false);
        guiGraphics.drawString(mc.font, Component.literal(speedText), x, y + (lineHeight + spacing) * 2, TEXT_COLOR, false);
    }

    /**
     * Рисует предупреждение о потере сигнала в стиле DJI
     */
    private void renderSignalLossWarning(GuiGraphics guiGraphics, int screenWidth, int screenHeight, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        String warningText = "ПОТЕРЯ СИГНАЛА";
        
        // Вычисляем размеры текста
        int textWidth = mc.font.width(warningText);
        int textHeight = mc.font.lineHeight;
        
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
        
        // Рисуем красный текст с пульсацией
        int textAlpha = (int) (0xFF * pulse);
        int textColor = (textAlpha << 24) | 0xFF0000; // Красный текст с пульсирующей яркостью
        
        // Рисуем текст с тенью для лучшей читаемости
        guiGraphics.drawString(mc.font, Component.literal(warningText), 
                              centerX - textWidth / 2 + 1, centerY - textHeight / 2 + 1, 
                              0x80000000, false); // Черная тень
        guiGraphics.drawString(mc.font, Component.literal(warningText), 
                              centerX - textWidth / 2, centerY - textHeight / 2, 
                              textColor, false); // Красный текст
    }
}

