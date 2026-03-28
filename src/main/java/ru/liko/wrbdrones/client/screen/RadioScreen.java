package ru.liko.wrbdrones.client.screen;

import ru.liko.wrbdrones.network.LaunchShahedPacket;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.wrbdrones.network.ModNetworking;

@OnlyIn(Dist.CLIENT)
public class RadioScreen extends Screen {

    private static final int GUI_WIDTH = 240;
    private static final int GUI_HEIGHT = 250;

    // Minimalist Colors
    private static final int COL_BACKGROUND = 0xF5101010;
    private static final int COL_BORDER = 0xFF404040;
    private static final int COL_TEXT_HEADER = 0xFFFFFFFF;
    private static final int COL_TEXT_LABEL = 0xFFAAAAAA;
    private static final int COL_TEXT_VALUE = 0xFF55FF55;
    private static final int COL_ACCENT = 0xFF333333;
    private static final int COL_ACCENT_HOVER = 0xFF505050;

    private final int shahedEntityId;
    private final int initialX;
    private final int initialY;
    private final int initialZ;
    private final int droneX;
    private final int droneY;
    private final int droneZ;

    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private EditBox speedField;
    private EditBox altitudeField;
    private Button launchButton;
    private Button evasiveButton;
    private boolean evasiveMode = false;

    private Button altMinus50;
    private Button altMinus10;
    private Button altPlus10;
    private Button altPlus50;
    private Button speedMinus;
    private Button speedPlus;

    public RadioScreen(int shahedEntityId, int initialX, int initialY, int initialZ, int droneX, int droneY,
            int droneZ) {
        super(Component.translatable("screen.wrbdrones.radio"));
        this.shahedEntityId = shahedEntityId;
        this.initialX = initialX;
        this.initialY = initialY;
        this.initialZ = initialZ;
        this.droneX = droneX;
        this.droneY = droneY;
        this.droneZ = droneZ;
    }

    @Override
    protected void init() {
        super.init();

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;
        int centerX = guiLeft + GUI_WIDTH / 2;

        int startY = guiTop + 50;
        int fieldHeight = 16;
        int fieldWidth = 60;
        int spacing = 10;

        // --- Coordinates Row ---
        // X
        this.xField = new EditBox(this.font, centerX - fieldWidth - spacing - fieldWidth / 2, startY, fieldWidth,
                fieldHeight, Component.literal("X"));
        this.xField.setMaxLength(10);
        this.xField.setValue(String.valueOf(initialX));
        this.xField.setFilter(this::isValidCoordinate);
        this.xField.setTextColor(COL_TEXT_VALUE);
        this.xField.setBordered(true);
        this.addRenderableWidget(this.xField);

        // Y
        this.yField = new EditBox(this.font, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight,
                Component.literal("Y"));
        this.yField.setMaxLength(10);
        this.yField.setValue(String.valueOf(initialY));
        this.yField.setFilter(this::isValidCoordinate);
        this.yField.setTextColor(COL_TEXT_VALUE);
        this.yField.setBordered(true);
        this.addRenderableWidget(this.yField);

        // Z
        this.zField = new EditBox(this.font, centerX + fieldWidth / 2 + spacing, startY, fieldWidth, fieldHeight,
                Component.literal("Z"));
        this.zField.setMaxLength(10);
        this.zField.setValue(String.valueOf(initialZ));
        this.zField.setFilter(this::isValidCoordinate);
        this.zField.setTextColor(COL_TEXT_VALUE);
        this.zField.setBordered(true);
        this.addRenderableWidget(this.zField);

        // --- Helper Buttons ---
        int btnY = startY + 22;
        int btnWidth = 55;
        int btnHeight = 16;
        int btnGap = 8;

        // Center the 3 buttons
        int totalBtnW = btnWidth * 3 + btnGap * 2;
        int btnStartX = centerX - totalBtnW / 2;

        this.addRenderableWidget(new MinimalButton(btnStartX, btnY, btnWidth, btnHeight,
                Component.translatable("screen.wrbdrones.radio.player"), b -> useCurrentPosition()));
        this.addRenderableWidget(new MinimalButton(btnStartX + btnWidth + btnGap, btnY, btnWidth, btnHeight,
                Component.translatable("screen.wrbdrones.radio.look"), b -> useLookPosition()));
        this.addRenderableWidget(new MinimalButton(btnStartX + (btnWidth + btnGap) * 2, btnY, btnWidth, btnHeight,
                Component.literal("Y=64"), b -> yField.setValue("64")));

        // --- Parameters ---
        int paramY = btnY + 32;
        int labelW = 80; // Increased width for labels
        int inputW = 40;
        int smallBtnW = 18;

        // Altitude
        int altStartX = guiLeft + 10;
        this.altitudeField = new EditBox(this.font, altStartX + labelW, paramY, inputW, fieldHeight,
                Component.literal("ALT"));
        this.altitudeField.setMaxLength(4);

        int defaultAlt = (int) Mth.clamp(80, ServerConfig.SHAHED136_MIN_ALTITUDE.get(),
                ServerConfig.SHAHED136_MAX_ALTITUDE.get());
        this.altitudeField.setValue(String.valueOf(defaultAlt));

        this.altitudeField.setFilter(this::isValidInt); // Changed to allow negative
        this.altitudeField.setTextColor(COL_TEXT_VALUE);
        this.altitudeField.setBordered(true);
        this.addRenderableWidget(this.altitudeField);

        int controlX = altStartX + labelW + inputW + 5;
        this.altMinus50 = new MinimalButton(controlX, paramY, smallBtnW, fieldHeight, Component.literal("--"),
                b -> adjustAltitude(-50));
        this.altMinus10 = new MinimalButton(controlX + 20, paramY, smallBtnW, fieldHeight, Component.literal("-"),
                b -> adjustAltitude(-10));
        this.altPlus10 = new MinimalButton(controlX + 40, paramY, smallBtnW, fieldHeight, Component.literal("+"),
                b -> adjustAltitude(10));
        this.altPlus50 = new MinimalButton(controlX + 60, paramY, smallBtnW, fieldHeight, Component.literal("++"),
                b -> adjustAltitude(50));

        this.addRenderableWidget(this.altMinus50);
        this.addRenderableWidget(this.altMinus10);
        this.addRenderableWidget(this.altPlus10);
        this.addRenderableWidget(this.altPlus50);

        // Speed
        int speedY = paramY + 30;
        this.speedField = new EditBox(this.font, altStartX + labelW, speedY, inputW, fieldHeight,
                Component.literal("SPD"));
        this.speedField.setMaxLength(4);

        int defaultSpeed = (int) Mth.clamp(180, ServerConfig.SHAHED136_MIN_SPEED_KMH.get(),
                ServerConfig.SHAHED136_MAX_SPEED_KMH.get());
        this.speedField.setValue(String.valueOf(defaultSpeed)); // Default clamped to config

        this.speedField.setFilter(this::isValidFloat);
        this.speedField.setTextColor(COL_TEXT_VALUE);
        this.speedField.setBordered(true);
        this.addRenderableWidget(this.speedField);

        this.speedMinus = new MinimalButton(controlX, speedY, 39, fieldHeight, Component.literal("-"),
                b -> adjustSpeed(-5));
        this.speedPlus = new MinimalButton(controlX + 41, speedY, 39, fieldHeight, Component.literal("+"),
                b -> adjustSpeed(5));

        this.addRenderableWidget(this.speedMinus);
        this.addRenderableWidget(this.speedPlus);

        // --- Actions ---
        int actionY = speedY + 35;

        this.evasiveButton = new MinimalButton(guiLeft + 20, actionY, GUI_WIDTH - 40, 18, getEvasiveButtonText(),
                b -> toggleEvasive());
        this.addRenderableWidget(this.evasiveButton);

        this.launchButton = new MinimalButton(guiLeft + 20, guiTop + GUI_HEIGHT - 30, GUI_WIDTH - 40, 20,
                Component.translatable("screen.wrbdrones.radio.launch").withStyle(ChatFormatting.BOLD),
                b -> onLaunch());
        this.addRenderableWidget(this.launchButton);
    }

    private void adjustAltitude(int delta) {
        try {
            int current = Integer.parseInt(altitudeField.getValue());
            int minAlt = ServerConfig.SHAHED136_MIN_ALTITUDE.get().intValue();
            int maxAlt = ServerConfig.SHAHED136_MAX_ALTITUDE.get().intValue();
            int newVal = Mth.clamp(current + delta, minAlt, maxAlt);
            altitudeField.setValue(String.valueOf(newVal));
        } catch (NumberFormatException e) {
            int defaultAlt = (int) Mth.clamp(80, ServerConfig.SHAHED136_MIN_ALTITUDE.get(),
                    ServerConfig.SHAHED136_MAX_ALTITUDE.get());
            altitudeField.setValue(String.valueOf(defaultAlt));
        }
    }

    private void adjustSpeed(int delta) {
        try {
            float current = Float.parseFloat(speedField.getValue());
            float minSpeed = ServerConfig.SHAHED136_MIN_SPEED_KMH.get().floatValue();
            float maxSpeed = ServerConfig.SHAHED136_MAX_SPEED_KMH.get().floatValue();
            float newVal = Mth.clamp(current + delta, minSpeed, maxSpeed);
            speedField.setValue(String.valueOf((int) newVal));
        } catch (NumberFormatException e) {
            int defaultSpeed = (int) Mth.clamp(180, ServerConfig.SHAHED136_MIN_SPEED_KMH.get(),
                    ServerConfig.SHAHED136_MAX_SPEED_KMH.get());
            speedField.setValue(String.valueOf(defaultSpeed));
        }
    }

    private void useLookPosition() {
        if (this.minecraft != null && this.minecraft.player != null) {
            var hitResult = this.minecraft.player.pick(500.0, 0.0f, false);
            if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
                this.xField.setValue(String.valueOf(blockHit.getBlockPos().getX()));
                this.yField.setValue(String.valueOf(blockHit.getBlockPos().getY()));
                this.zField.setValue(String.valueOf(blockHit.getBlockPos().getZ()));
            }
        }
    }

    private Component getEvasiveButtonText() {
        return evasiveMode
                ? Component.translatable("screen.wrbdrones.radio.maneuver_on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("screen.wrbdrones.radio.maneuver_off").withStyle(ChatFormatting.RED);
    }

    private void toggleEvasive() {
        evasiveMode = !evasiveMode;
        evasiveButton.setMessage(getEvasiveButtonText());
    }

    private boolean isValidCoordinate(String text) {
        if (text.isEmpty() || text.equals("-")) {
            return true;
        }
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidFloat(String text) {
        if (text.isEmpty())
            return true;
        try {
            Float.parseFloat(text);
            return true;
        } catch (NumberFormatException e) {
            return text.equals(".");
        }
    }

    private boolean isValidInt(String text) {
        if (text.isEmpty() || text.equals("-"))
            return true;
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void useCurrentPosition() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.xField.setValue(String.valueOf((int) this.minecraft.player.getX()));
            this.yField.setValue(String.valueOf((int) this.minecraft.player.getY()));
            this.zField.setValue(String.valueOf((int) this.minecraft.player.getZ()));
        }
    }

    private void onLaunch() {
        try {
            int x = parseCoordinate(xField.getValue());
            int y = parseCoordinate(yField.getValue());
            int z = parseCoordinate(zField.getValue());
            float speedKmh = parseFloat(speedField.getValue(), 180f);
            float altitude = parseFloat(altitudeField.getValue(), 80f);

            double maxDist = ServerConfig.SHAHED136_MAX_DISTANCE.get();
            if (calculateDistance() > maxDist) {
                // Warning is now handled in render
                return;
            }

            if (isSpeedInvalid()) {
                return;
            }

            if (isAltitudeInvalid()) {
                return;
            }

            double minSpeed = ServerConfig.SHAHED136_MIN_SPEED_KMH.get();
            double maxSpeed = ServerConfig.SHAHED136_MAX_SPEED_KMH.get();
            double minAlt = ServerConfig.SHAHED136_MIN_ALTITUDE.get();
            double maxAlt = ServerConfig.SHAHED136_MAX_ALTITUDE.get();

            speedKmh = Mth.clamp(speedKmh, (float) minSpeed, (float) maxSpeed);
            altitude = Mth.clamp(altitude, (float) minAlt, (float) maxAlt);

            // Convert km/h to blocks/tick (1 b/t = 72 km/h)
            float speedBlocksPerTick = speedKmh / 72.0f;

            PacketDistributor.sendToServer(
                    new LaunchShahedPacket(shahedEntityId, x, y, z, speedBlocksPerTick, altitude, evasiveMode));
            this.onClose();
        } catch (NumberFormatException e) {
            // Invalid coordinates
        }
    }

    private int parseCoordinate(String value) throws NumberFormatException {
        if (value.isEmpty() || value.equals("-")) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private float parseFloat(String value, float defaultVal) {
        if (value.isEmpty())
            return defaultVal;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private double calculateDistance() {
        if (this.minecraft == null || this.minecraft.player == null)
            return 0;
        try {
            int tx = parseCoordinate(xField.getValue());
            int ty = parseCoordinate(yField.getValue());
            int tz = parseCoordinate(zField.getValue());
            double dx = tx - droneX;
            double dy = ty - droneY;
            double dz = tz - droneZ;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        } catch (Exception e) {
            return 0;
        }
    }

    private double calculateETA() {
        double dist = calculateDistance();
        float speedKmh = parseFloat(speedField.getValue(), 180f);
        // Convert km/h to blocks/sec: (kmh / 72) * 20
        float speedBlocksPerSec = (speedKmh / 72.0f) * 20.0f;
        if (speedBlocksPerSec <= 0)
            return 0;
        return dist / speedBlocksPerSec;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        // 1. Main Background
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, COL_BACKGROUND);

        // 2. Simple Border
        graphics.renderOutline(guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, COL_BORDER);

        // 3. Header
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 24, 0xFF151515);
        graphics.fill(guiLeft, guiTop + 24, guiLeft + GUI_WIDTH, guiTop + 25, COL_BORDER);
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrbdrones.radio.header"), this.width / 2,
                guiTop + 8, COL_TEXT_HEADER);

        // 4. Labels
        int startY = guiTop + 50;
        // Coordinate labels above fields
        drawLabelCentered(graphics, "X", xField.getX() + xField.getWidth() / 2, xField.getY() - 10);
        drawLabelCentered(graphics, "Y", yField.getX() + yField.getWidth() / 2, yField.getY() - 10);
        drawLabelCentered(graphics, "Z", zField.getX() + zField.getWidth() / 2, zField.getY() - 10);

        // Parameter labels to the left of fields
        int btnY = startY + 22;
        int paramY = btnY + 32;
        int speedY = paramY + 30;

        graphics.drawString(this.font, Component.translatable("screen.wrbdrones.radio.alt"), guiLeft + 10, paramY + 4,
                COL_TEXT_LABEL, false);
        graphics.drawString(this.font, Component.translatable("screen.wrbdrones.radio.spd"), guiLeft + 10, speedY + 4,
                COL_TEXT_LABEL, false);

        // 6. Status Info at Bottom
        int statusY = guiTop + GUI_HEIGHT - 60;
        double distance = calculateDistance();
        double maxDist = ServerConfig.SHAHED136_MAX_DISTANCE.get();

        if (distance > maxDist) {
            String warningStr = Component.translatable("screen.wrbdrones.radio.warning.too_far", (int) maxDist)
                    .getString();
            graphics.drawCenteredString(this.font, warningStr, this.width / 2, statusY, 0xFFFF5555);
        } else if (isSpeedInvalid()) {
            double minSpeed = ServerConfig.SHAHED136_MIN_SPEED_KMH.get();
            double maxSpeed = ServerConfig.SHAHED136_MAX_SPEED_KMH.get();
            String warningStr = Component
                    .translatable("screen.wrbdrones.radio.warning.invalid_speed", (int) minSpeed, (int) maxSpeed)
                    .getString();
            graphics.drawCenteredString(this.font, warningStr, this.width / 2, statusY, 0xFFFF5555);
        } else if (isAltitudeInvalid()) {
            double minAlt = ServerConfig.SHAHED136_MIN_ALTITUDE.get();
            double maxAlt = ServerConfig.SHAHED136_MAX_ALTITUDE.get();
            String warningStr = Component
                    .translatable("screen.wrbdrones.radio.warning.invalid_alt", (int) minAlt, (int) maxAlt).getString();
            graphics.drawCenteredString(this.font, warningStr, this.width / 2, statusY, 0xFFFF5555);
        } else {
            double eta = calculateETA();
            String distStr = Component.translatable("screen.wrbdrones.radio.dist", String.format("%.1f", distance))
                    .getString();
            String etaStr = Component.translatable("screen.wrbdrones.radio.eta", String.format("%.2f", eta))
                    .getString();
            graphics.drawCenteredString(this.font, distStr + "   " + etaStr, this.width / 2, statusY, COL_TEXT_LABEL);
        }
    }

    private boolean isSpeedInvalid() {
        float val = parseFloat(speedField.getValue(), 0f);
        double minSpeed = ServerConfig.SHAHED136_MIN_SPEED_KMH.get();
        double maxSpeed = ServerConfig.SHAHED136_MAX_SPEED_KMH.get();
        return val < minSpeed || val > maxSpeed;
    }

    private boolean isAltitudeInvalid() {
        float val = parseFloat(altitudeField.getValue(), 0f);
        double minAlt = ServerConfig.SHAHED136_MIN_ALTITUDE.get();
        double maxAlt = ServerConfig.SHAHED136_MAX_ALTITUDE.get();
        return val < minAlt || val > maxAlt;
    }

    private void drawLabelCentered(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawCenteredString(this.font, text, x, y, COL_TEXT_LABEL);
    }

    private class MinimalButton extends Button {
        public MinimalButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int bgColor = this.active ? (this.isHoveredOrFocused() ? COL_ACCENT_HOVER : COL_ACCENT) : 0xFF202020;
            int textColor = this.active ? 0xFFE0E0E0 : 0xFF555555;

            graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            graphics.renderOutline(getX(), getY(), width, height, COL_BORDER);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.xField.isFocused() || this.yField.isFocused() || this.zField.isFocused()
                || this.speedField.isFocused() || this.altitudeField.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
