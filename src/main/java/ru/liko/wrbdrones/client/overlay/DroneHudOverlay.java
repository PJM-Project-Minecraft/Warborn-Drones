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
import ru.liko.wrbdrones.util.RebUtils;

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
        
        // Если дрон уничтожен/удален - прекращаем рендеринг оверлея
        if (drone.isRemoved())
            return;
            
        if (addonDrone instanceof MavicDroneWithDropEntity || addonDrone instanceof MavicDroneNoDropEntity)
            return;

        int square = Math.min(screenWidth, screenHeight);
        int visibleWidth = (int) (square * 0.40f);
        int sideBarWidth = (screenWidth - visibleWidth) / 2;
        sideBarWidth = (int) (sideBarWidth * 0.65f * 0.65f);
        sideBarWidth = Math.max(sideBarWidth, 80);

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

        // Используем позицию аватара для расчета дистанции, если дрон -
        // AddonDroneEntity
        Vec3 operatorPos = player.position();
        Vec3 avatarPos = addonDrone.getOperatorPosition();
        if (avatarPos != null) {
            operatorPos = avatarPos;
        }
        double distance = operatorPos.distanceTo(drone.position());

        // Получаем коэффициент глушения сигнала от РЭБ (0.0 - 1.0, где 1.0 = полное
        // глушение)
        double rebJammingFactor = getRebJammingFactor(addonDrone);

        // Вычисляем уровень сигнала для использования в шуме и логике дрона
        double maxDistance = ServerConfig.FPV_MAX_DISTANCE.get();
        double signalPercent = Math.max(0.0, 1.0 - (distance / maxDistance));
        signalPercent = signalPercent * signalPercent; // Квадратичная функция для плавного затухания

        // Применяем глушение от РЭБ (чем ближе к РЭБ, тем сильнее глушение)
        signalPercent = signalPercent * (1.0 - rebJammingFactor);

        double signalStrength = Math.max(0.0, Math.min(1.0, signalPercent));

        int signal = (int) Math.max(0, Math.min(99, signalStrength * 100));

        // 1. Рендерим рамку и прицел
        renderFrameAndCrosshair(gfx, screenWidth, screenHeight);

        // 2. Рендерим боковые полосы (черные)
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        renderSideBars(gfx, screenWidth, screenHeight, sideBarWidth);

        // 3. Рендерим скобки прицеливания и индикаторы
        renderTargetingBrackets(gfx, screenWidth, screenHeight, sideBarWidth);
        renderIndicators(gfx, screenWidth, screenHeight, sideBarWidth, drone, player, distance, monitor, signal);

        // 4. Рендерим шум поверх всего в видимой области (не на боковых полосах
        // благодаря scissor test)
        // При сигнале 0 шум максимальный
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
        if (signal <= 1) {
            if (SIGNAL_LOSS_REPORTED.add(droneId)) {
                PacketDistributor.sendToServer(new DroneSignalLostPacket(droneId));
            }
        } else {
            SIGNAL_LOSS_REPORTED.remove(droneId);
        }
    }

    private static void renderFrameAndCrosshair(GuiGraphics gfx, int screenWidth, int screenHeight) {
        gfx.blit(CROSSHAIR, screenWidth / 2 - 16, screenHeight / 2 - 16, 0, 0, 32, 32, 32, 32);
        float addW = (float) screenWidth / screenHeight * 48f;
        float addH = (float) screenWidth / screenHeight * 27f;
        preciseBlit(gfx, TV_FRAME, -addW / 2f, -addH / 2f, 10, 0, 0,
                screenWidth + addW, screenHeight + addH, screenWidth + addW, screenHeight + addH);
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

        drawDashedLine(buffer, matrix, centerX - bracketWidth, top, centerX - bracketWidth, bottom, 8, 4,
                TARGETING_BRACKET_COLOR);
        drawDashedLine(buffer, matrix, centerX + bracketWidth, top, centerX + bracketWidth, bottom, 8, 4,
                TARGETING_BRACKET_COLOR);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void drawDashedLine(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2,
            int dashLength, int dashGap, int color) {
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

    private static void renderIndicators(GuiGraphics gfx, int screenWidth, int screenHeight, int sideBarWidth,
            DroneEntity drone,
            Player player, double distance, ItemStack monitor, int signal) {
        Minecraft mc = Minecraft.getInstance();

        if (!(drone instanceof AddonDroneEntity))
            return;

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

        String callsign = controller != null ? controller.getDisplayName().getString() : "CROCUS";
        int centerX = screenWidth / 2;
        drawOutlinedString(gfx, mc.font, Component.literal(callsign), centerX, screenHeight / 2 - 60, TEXT_COLOR, true);

        // Дистанция в левой части (желтая зона)
        String distanceText = String.format("%.1fm", distance);
        int distanceX = sideBarWidth + 10; // Позиция текста дистанции с учетом боковой полосы
        drawOutlinedString(gfx, mc.font, Component.literal(distanceText), distanceX, screenHeight - 60, TEXT_COLOR, false);

        // Сигнал в правой части (красная зона) с иконкой

        // Рисуем число сигнала справа
        String signalText = String.format("%02d", signal);
        int signalTextX = screenWidth - sideBarWidth - 10; // Позиция текста сигнала справа
        int signalY = screenHeight - 60;
        int signalTextWidth = mc.font.width(signalText);
        signalTextX -= signalTextWidth; // Выравниваем по правому краю

        // Рисуем иконку сигнала (4 полоски) слева от текста
        int signalIconX = signalTextX - 25; // Позиция иконки сигнала слева от текста
        renderSignalIcon(gfx, signalIconX, signalY, signal);

        // Рисуем число сигнала
        drawOutlinedString(gfx, mc.font, Component.literal(signalText), signalTextX, signalY, TEXT_COLOR, false);
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

    private static void renderSignalIcon(GuiGraphics gfx, int x, int y, int signal) {
        // Определяем количество активных полосок на основе уровня сигнала
        // 0-24: 1 полоска, 25-49: 2 полоски, 50-74: 3 полоски, 75-99: 4 полоски
        int activeBars = 1;
        if (signal >= 25)
            activeBars = 2;
        if (signal >= 50)
            activeBars = 3;
        if (signal >= 75)
            activeBars = 4;

        // Параметры иконки
        int barWidth = 3; // Ширина каждой полоски
        int barSpacing = 2; // Расстояние между полосками
        int[] barHeights = { 4, 6, 8, 10 }; // Высота каждой полоски (от короткой к длинной)
        int maxBarHeight = 10; // Максимальная высота полоски

        // Выравнивание иконки по вертикали с текстом
        // Текст выравнивается по базовой линии, поэтому иконку выравниваем по нижнему
        // краю
        int iconBaseY = y + 1; // Небольшой отступ для выравнивания с текстом

        // Рисуем 4 полоски
        for (int i = 0; i < 4; i++) {
            int barX = x + i * (barWidth + barSpacing);
            int barHeight = barHeights[i];
            int barY = iconBaseY + (maxBarHeight - barHeight); // Выравнивание по нижнему краю

            // Цвет полоски: белый если активна, серый если неактивна
            int color = (i < activeBars) ? TEXT_COLOR : 0xFF808080;

            // Рисуем полоску
            gfx.fill(barX, barY, barX + barWidth, barY + barHeight, color);
        }
    }

    /**
     * Возвращает коэффициент глушения сигнала от РЭБ.
     */
    private static double getRebJammingFactor(AddonDroneEntity drone) {
        return RebUtils.getRebFactor(drone);
    }
}
