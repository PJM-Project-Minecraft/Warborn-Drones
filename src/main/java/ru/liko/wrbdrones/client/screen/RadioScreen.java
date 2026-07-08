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
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.wrbdrones.network.ModNetworking;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RadioScreen extends Screen {

    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 324;

    // Minimalist Colors
    private static final int COL_BACKGROUND = 0xF5101010;
    private static final int COL_BORDER = 0xFF404040;
    private static final int COL_TEXT_HEADER = 0xFFFFFFFF;
    private static final int COL_TEXT_LABEL = 0xFFAAAAAA;
    private static final int COL_TEXT_VALUE = 0xFF55FF55;
    private static final int COL_ACCENT = 0xFF333333;
    private static final int COL_ACCENT_HOVER = 0xFF505050;
    // Панели «планшета»: чуть светлее фона, тонкий бордер, заголовок-вкладка сверху.
    private static final int COL_PANEL = 0xFF181818;
    private static final int COL_PANEL_HEADER = 0xFF222222;
    private static final int COL_PANEL_BORDER = 0xFF2E2E2E;

    private final int shahedEntityId;
    private final int initialX;
    private final int initialY;
    private final int initialZ;
    private final int droneX;
    private final int droneY;
    private final int droneZ;

    private final double minSpeed;
    private final double maxSpeed;
    private final double minAltitude;
    private final double maxAltitude;
    private final double maxDistance;
    private final int maxWaypoints;
    private final boolean terrainFollowAllowed;

    private EditBox xField;
    private EditBox yField;
    private EditBox zField;
    private EditBox speedField;
    private EditBox altitudeField;
    private Button launchButton;
    private Button evasiveButton;
    private boolean evasiveMode = false;

    // Маршрут / terrain-follow
    private final List<int[]> waypoints = new ArrayList<>();
    private boolean terrainFollow = false;
    private Button terrainFollowButton;
    private EditBox wpXField;
    private EditBox wpYField;
    private EditBox wpZField;
    private Button addWaypointButton;
    private Button clearWaypointsButton;
    private Button removeLastWaypointButton;

    private Button altMinus50;
    private Button altMinus10;
    private Button altPlus10;
    private Button altPlus50;
    private Button speedMinus;
    private Button speedPlus;

    public RadioScreen(int shahedEntityId, int initialX, int initialY, int initialZ, int droneX, int droneY,
            int droneZ, boolean terrainFollow, List<int[]> waypoints,
            double minSpeed, double maxSpeed, double minAltitude, double maxAltitude,
            double maxDistance, int maxWaypoints, boolean terrainFollowAllowed) {
        super(Component.translatable("screen.wrbdrones.radio"));
        this.shahedEntityId = shahedEntityId;
        this.initialX = initialX;
        this.initialY = initialY;
        this.initialZ = initialZ;
        this.droneX = droneX;
        this.droneY = droneY;
        this.droneZ = droneZ;
        this.terrainFollow = terrainFollow;
        this.waypoints.addAll(waypoints);
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.minAltitude = minAltitude;
        this.maxAltitude = maxAltitude;
        this.maxDistance = maxDistance;
        this.maxWaypoints = maxWaypoints;
        this.terrainFollowAllowed = terrainFollowAllowed;
    }

    @Override
    protected void init() {
        super.init();

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        int fieldHeight = 16;

        // ═══ ЛЕВАЯ ПАНЕЛЬ: ЦЕЛЬ ═══  (guiLeft+8 .. guiLeft+150, высота 78)
        int leftX = guiLeft + 8;
        int leftW = 142;
        int row1Y = guiTop + 54; // первый ряд внутри панели (вкладка-заголовок 32, +22)
        int row2Y = guiTop + 76;

        // X / Y / Z — 3 поля по 40, gap 5
        int fw = 40;
        int fg = 5;
        int fx0 = leftX + 5; // 13
        this.xField = mkCoord(fx0, row1Y, fw, fieldHeight, "X", String.valueOf(initialX));
        this.addRenderableWidget(this.xField);
        this.yField = mkCoord(fx0 + (fw + fg), row1Y, fw, fieldHeight, "Y", String.valueOf(initialY));
        this.addRenderableWidget(this.yField);
        this.zField = mkCoord(fx0 + (fw + fg) * 2, row1Y, fw, fieldHeight, "Z", String.valueOf(initialZ));
        this.addRenderableWidget(this.zField);

        // Игрок / Взгляд — 2 кнопки по 65, gap 5
        int bw = 65;
        int bg = 5;
        int bx0 = leftX + 5;
        this.addRenderableWidget(new MinimalButton(bx0, row2Y, bw, fieldHeight,
                Component.translatable("screen.wrbdrones.radio.player"), b -> useCurrentPosition()));
        this.addRenderableWidget(new MinimalButton(bx0 + bw + bg, row2Y, bw, fieldHeight,
                Component.translatable("screen.wrbdrones.radio.look"), b -> useLookPosition()));

        // ═══ ПРАВАЯ ПАНЕЛЬ: ПАРАМЕТРЫ ═══  (guiLeft+150 .. guiLeft+292, высота 78)
        int rightX = guiLeft + 150;
        int rightW = 142;
        int pfX = rightX + 31; // 180 — поле (слева лейбл «ВЫС»/«СКР»)
        int pfw = 36;
        int pbw = 28;
        int pbg = 2;
        int pb0 = pfX + pfw + pbg; // 218

        // Высота: лейбл + поле + --/++
        this.altitudeField = new EditBox(this.font, pfX, row1Y, pfw, fieldHeight, Component.literal("ALT"));
        this.altitudeField.setMaxLength(4);
        this.altitudeField.setValue(String.valueOf((int) Mth.clamp(80, minAltitude,
                maxAltitude)));
        this.altitudeField.setFilter(this::isValidInt);
        this.altitudeField.setTextColor(COL_TEXT_VALUE);
        this.altitudeField.setBordered(true);
        this.addRenderableWidget(this.altitudeField);
        this.altMinus50 = new MinimalButton(pb0, row1Y, pbw, fieldHeight, Component.literal("--"),
                b -> adjustAltitude(-50));
        this.altPlus50 = new MinimalButton(pb0 + (pbw + pbg), row1Y, pbw, fieldHeight, Component.literal("++"),
                b -> adjustAltitude(50));
        this.addRenderableWidget(this.altMinus50);
        this.addRenderableWidget(this.altPlus50);

        // Скорость: лейбл + поле + -/+
        this.speedField = new EditBox(this.font, pfX, row2Y, pfw, fieldHeight, Component.literal("SPD"));
        this.speedField.setMaxLength(4);
        this.speedField.setValue(String.valueOf((int) Mth.clamp(180, minSpeed,
                maxSpeed)));
        this.speedField.setFilter(this::isValidFloat);
        this.speedField.setTextColor(COL_TEXT_VALUE);
        this.speedField.setBordered(true);
        this.addRenderableWidget(this.speedField);
        this.speedMinus = new MinimalButton(pb0, row2Y, pbw, fieldHeight, Component.literal("-"),
                b -> adjustSpeed(-5));
        this.speedPlus = new MinimalButton(pb0 + (pbw + pbg), row2Y, pbw, fieldHeight, Component.literal("+"),
                b -> adjustSpeed(5));
        this.addRenderableWidget(this.speedMinus);
        this.addRenderableWidget(this.speedPlus);

        // ═══ РЯД ТУМБЛЕРОВ ═══  (под верхними панелями, 2 кнопки на всю ширину)
        int togY = guiTop + 116;
        int togH = 18;
        this.evasiveButton = new MinimalButton(leftX, togY, leftW, togH, getEvasiveButtonText(),
                b -> toggleEvasive());
        this.addRenderableWidget(this.evasiveButton);
        this.terrainFollowButton = new MinimalButton(rightX, togY, rightW, togH, getTerrainFollowButtonText(),
                b -> toggleTerrainFollow());
        this.addRenderableWidget(this.terrainFollowButton);

        // ═══ НИЖНЯЯ ПАНЕЛЬ: МАРШРУТ ═══  (guiLeft+8 .. guiLeft+292, высота 110)
        int routeY = guiTop + 140;
        int wpRow1 = routeY + 24; // поля WX/WY/WZ
        int wpRow2 = routeY + 46; // кнопки
        int wpFx0 = leftX + 5;    // 13
        this.wpXField = mkCoord(wpFx0, wpRow1, fw, fieldHeight, "WX", "");
        this.addRenderableWidget(this.wpXField);
        this.wpYField = mkCoord(wpFx0 + (fw + fg), wpRow1, fw, fieldHeight, "WY", "");
        this.addRenderableWidget(this.wpYField);
        this.wpZField = mkCoord(wpFx0 + (fw + fg) * 2, wpRow1, fw, fieldHeight, "WZ", "");
        this.addRenderableWidget(this.wpZField);

        // 4 кнопки маршрута: 66×4 + gap 6×3 = 282 (панель 284 inner)
        int wpBW = 66;
        int wpBG = 6;
        int wpB0 = guiLeft + 8;
        this.addWaypointButton = new MinimalButton(wpB0, wpRow2, wpBW, fieldHeight,
                Component.translatable("screen.wrbdrones.radio.wp_add"), b -> addWaypoint());
        this.addRenderableWidget(this.addWaypointButton);
        this.addRenderableWidget(new MinimalButton(wpB0 + (wpBW + wpBG), wpRow2, wpBW, fieldHeight,
                Component.translatable("screen.wrbdrones.radio.wp_look"), b -> useLookForWaypoint()));
        this.removeLastWaypointButton = new MinimalButton(wpB0 + (wpBW + wpBG) * 2, wpRow2, wpBW, fieldHeight,
                Component.translatable("screen.wrbdrones.radio.wp_remove_last"), b -> removeLastWaypoint());
        this.addRenderableWidget(this.removeLastWaypointButton);
        this.clearWaypointsButton = new MinimalButton(wpB0 + (wpBW + wpBG) * 3, wpRow2, wpBW, fieldHeight,
                Component.translatable("screen.wrbdrones.radio.wp_clear"), b -> clearWaypoints());
        this.addRenderableWidget(this.clearWaypointsButton);

        // ═══ ЗАПУСК ═══
        this.launchButton = new MinimalButton(guiLeft + 8, guiTop + GUI_HEIGHT - 26, GUI_WIDTH - 16, 18,
                Component.translatable("screen.wrbdrones.radio.launch").withStyle(ChatFormatting.BOLD),
                b -> onLaunch());
        this.addRenderableWidget(this.launchButton);
    }

    /** Фабрика EditBox координаты (фильтр int, зелёный текст, бордер). */
    private EditBox mkCoord(int x, int y, int w, int h, String hint, String initial) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(hint));
        box.setMaxLength(10);
        box.setValue(initial);
        box.setFilter(this::isValidCoordinate);
        box.setTextColor(COL_TEXT_VALUE);
        box.setBordered(true);
        return box;
    }

    // ── Waypoint / terrain-follow helpers ──

    private int maxWaypoints() {
        return Math.max(0, maxWaypoints);
    }

    private void addWaypoint() {
        if (waypoints.size() >= maxWaypoints()) {
            return;
        }
        try {
            int x = parseCoordinate(wpXField.getValue());
            int y = parseCoordinate(wpYField.getValue());
            int z = parseCoordinate(wpZField.getValue());
            waypoints.add(new int[]{x, y, z});
            wpXField.setValue("");
            wpYField.setValue("");
            wpZField.setValue("");
        } catch (NumberFormatException ignored) {
        }
    }

    private void removeLastWaypoint() {
        if (!waypoints.isEmpty()) {
            waypoints.remove(waypoints.size() - 1);
        }
    }

    private void clearWaypoints() {
        waypoints.clear();
    }

    private void useLookForWaypoint() {
        if (this.minecraft != null && this.minecraft.player != null) {
            var hitResult = this.minecraft.player.pick(500.0, 0.0f, false);
            if (hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var blockHit = (net.minecraft.world.phys.BlockHitResult) hitResult;
                this.wpXField.setValue(String.valueOf(blockHit.getBlockPos().getX()));
                this.wpYField.setValue(String.valueOf(blockHit.getBlockPos().getY()));
                this.wpZField.setValue(String.valueOf(blockHit.getBlockPos().getZ()));
            }
        }
    }

    private Component getTerrainFollowButtonText() {
        if (!terrainFollowAllowed) {
            return Component.translatable("screen.wrbdrones.radio.tf_disabled").withStyle(ChatFormatting.DARK_GRAY);
        }
        return terrainFollow
                ? Component.translatable("screen.wrbdrones.radio.tf_on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("screen.wrbdrones.radio.tf_off").withStyle(ChatFormatting.RED);
    }

    private void toggleTerrainFollow() {
        if (terrainFollowAllowed) {
            terrainFollow = !terrainFollow;
            terrainFollowButton.setMessage(getTerrainFollowButtonText());
        }
    }

    private void adjustAltitude(int delta) {
        try {
            int current = Integer.parseInt(altitudeField.getValue());
            int minAlt = (int) minAltitude;
            int maxAlt = (int) maxAltitude;
            int newVal = Mth.clamp(current + delta, minAlt, maxAlt);
            altitudeField.setValue(String.valueOf(newVal));
        } catch (NumberFormatException e) {
            int defaultAlt = (int) Mth.clamp(80, minAltitude,
                    maxAltitude);
            altitudeField.setValue(String.valueOf(defaultAlt));
        }
    }

    private void adjustSpeed(int delta) {
        try {
            float current = Float.parseFloat(speedField.getValue());
            float minSpeedVal = (float) minSpeed;
            float maxSpeedVal = (float) maxSpeed;
            float newVal = Mth.clamp(current + delta, minSpeedVal, maxSpeedVal);
            speedField.setValue(String.valueOf((int) newVal));
        } catch (NumberFormatException e) {
            int defaultSpeed = (int) Mth.clamp(180, minSpeed,
                    maxSpeed);
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

            double maxDist = maxDistance;
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

            double minAlt = minAltitude;
            double maxAlt = maxAltitude;

            speedKmh = Mth.clamp(speedKmh, (float) minSpeed, (float) maxSpeed);
            altitude = Mth.clamp(altitude, (float) minAlt, (float) maxAlt);

            // Convert km/h to blocks/tick (1 b/t = 72 km/h)
            float speedBlocksPerTick = speedKmh / 72.0f;

            PacketDistributor.sendToServer(
                    new LaunchShahedPacket(shahedEntityId, x, y, z, speedBlocksPerTick, altitude, evasiveMode,
                            terrainFollow, new ArrayList<>(waypoints)));
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
        int leftX = guiLeft + 8;
        int rightX = guiLeft + 150;
        int routeX = guiLeft + 8;
        int routeW = GUI_WIDTH - 16; // 284

        // 1. Фон + бордер «планшета»
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, COL_BACKGROUND);
        graphics.renderOutline(guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, COL_BORDER);

        // 2. Заголовок окна
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 24, 0xFF151515);
        graphics.fill(guiLeft, guiTop + 24, guiLeft + GUI_WIDTH, guiTop + 25, COL_BORDER);
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrbdrones.radio.header"),
                this.width / 2, guiTop + 8, COL_TEXT_HEADER);

        // 3. Панели
        drawPanel(graphics, leftX, guiTop + 32, 142, 78, Component.translatable("screen.wrbdrones.radio.target"));
        drawPanel(graphics, rightX, guiTop + 32, 142, 78, Component.translatable("screen.wrbdrones.radio.params"));
        drawPanel(graphics, routeX, guiTop + 140, routeW, 110,
                Component.translatable("screen.wrbdrones.radio.wp_list", waypoints.size(), maxWaypoints()));

        // 4. Лейблы X/Y/Z над полями (под вкладкой панели)
        int xyzLblY = guiTop + 47;
        drawLabelCentered(graphics, "X", xField.getX() + xField.getWidth() / 2, xyzLblY);
        drawLabelCentered(graphics, "Y", yField.getX() + yField.getWidth() / 2, xyzLblY);
        drawLabelCentered(graphics, "Z", zField.getX() + zField.getWidth() / 2, xyzLblY);

        // 5. Лейблы ВЫС/СКР слева от полей (правая панель)
        graphics.drawString(this.font, Component.translatable("screen.wrbdrones.radio.alt"),
                rightX + 5, xField.getY() + 4, COL_TEXT_LABEL, false);
        graphics.drawString(this.font, Component.translatable("screen.wrbdrones.radio.spd"),
                rightX + 5, speedField.getY() + 4, COL_TEXT_LABEL, false);

        // 6. Лейблы WX/WY/WZ над полями маршрута
        int wpLblY = guiTop + 157;
        drawLabelCentered(graphics, "WX", wpXField.getX() + wpXField.getWidth() / 2, wpLblY);
        drawLabelCentered(graphics, "WY", wpYField.getX() + wpYField.getWidth() / 2, wpLblY);
        drawLabelCentered(graphics, "WZ", wpZField.getX() + wpZField.getWidth() / 2, wpLblY);

        // 7. Список маршрута (под кнопками)
        int listY = clearWaypointsButton.getY() + clearWaypointsButton.getHeight() + 4;
        int rowY = listY;
        int maxRows = Math.min(waypoints.size(), 3);
        for (int i = 0; i < maxRows; i++) {
            int[] wp = waypoints.get(i);
            String row = String.format("§f%d. §a%d %d %d", i + 1, wp[0], wp[1], wp[2]);
            graphics.drawCenteredString(this.font, row, this.width / 2, rowY + i * 9, COL_TEXT_VALUE);
        }
        if (waypoints.size() > 3) {
            graphics.drawCenteredString(this.font, "§7+" + (waypoints.size() - 3), this.width / 2,
                    rowY + 3 * 9, COL_TEXT_LABEL);
        }

        // 8. Статус над кнопкой запуска
        int statusY = guiTop + GUI_HEIGHT - 44;
        double distance = calculateDistance();
        double maxDist = maxDistance;

        if (distance > maxDist) {
            String warningStr = Component.translatable("screen.wrbdrones.radio.warning.too_far", (int) maxDist)
                    .getString();
            graphics.drawCenteredString(this.font, warningStr, this.width / 2, statusY, 0xFFFF5555);
        } else if (isSpeedInvalid()) {
            String warningStr = Component
                    .translatable("screen.wrbdrones.radio.warning.invalid_speed", (int) minSpeed, (int) maxSpeed)
                    .getString();
            graphics.drawCenteredString(this.font, warningStr, this.width / 2, statusY, 0xFFFF5555);
        } else if (isAltitudeInvalid()) {
            double minAlt = minAltitude;
            double maxAlt = maxAltitude;
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
        return val < minSpeed || val > maxSpeed;
    }

    private boolean isAltitudeInvalid() {
        float val = parseFloat(altitudeField.getValue(), 0f);
        double minAlt = minAltitude;
        double maxAlt = maxAltitude;
        return val < minAlt || val > maxAlt;
    }

    private void drawLabelCentered(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawCenteredString(this.font, text, x, y, COL_TEXT_LABEL);
    }

    /** Панель «планшета»: блок с тонким бордером и заголовком-вкладкой сверху. */
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, Component title) {
        graphics.fill(x, y, x + w, y + h, COL_PANEL);
        graphics.renderOutline(x, y, w, h, COL_PANEL_BORDER);
        graphics.fill(x, y, x + w, y + 14, COL_PANEL_HEADER);
        graphics.fill(x, y + 14, x + w, y + 15, COL_PANEL_BORDER);
        graphics.drawCenteredString(this.font, title, x + w / 2, y + 3, COL_TEXT_LABEL);
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
                || this.speedField.isFocused() || this.altitudeField.isFocused()
                || this.wpXField.isFocused() || this.wpYField.isFocused() || this.wpZField.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
