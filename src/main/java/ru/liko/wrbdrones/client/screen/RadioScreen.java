package ru.liko.wrbdrones.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.network.LaunchShahedPacket;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class RadioScreen extends Screen {

    // ── Размеры окна ─────────────────────────────────────────────────────────
    private static final int GUI_WIDTH = 300;

    // ── Единый грид (все отступы кратны, чтобы ничего не «косило») ────────────
    private static final int PAD = 8;          // внешний отступ
    private static final int GAP = 6;          // зазор между панелями/рядами
    private static final int HEADER_H = 22;     // строка заголовка окна
    private static final int PANEL_HDR = 13;    // высота вкладки-заголовка панели
    private static final int FIELD_H = 16;
    private static final int BTN_H = 16;
    private static final int LABEL_H = 10;       // место под лейбл над полем

    private static final int PANEL_W = (GUI_WIDTH - 2 * PAD - GAP) / 2;   // 139
    private static final int PANEL_TOP_H = 72;
    private static final int TOG_H = 18;
    private static final int ROUTE_W = GUI_WIDTH - 2 * PAD;               // 284
    private static final int ROUTE_H = 114;

    // Y-смещения от guiTop
    private static final int PANEL_TOP_Y = HEADER_H + GAP;                       // 28
    private static final int TOG_Y = PANEL_TOP_Y + PANEL_TOP_H + GAP;            // 106
    private static final int ROUTE_Y = TOG_Y + TOG_H + GAP;                      // 130
    private static final int STATUS_Y = ROUTE_Y + ROUTE_H + 8;                   // 252
    private static final int LAUNCH_Y = STATUS_Y + 12;                          // 264
    private static final int GUI_HEIGHT = LAUNCH_Y + BTN_H + 2 + PAD;            // 292

    // Ряды внутри верхних панелей
    private static final int TP_CONTENT = PANEL_TOP_Y + PANEL_HDR + 4;           // 45
    private static final int TP_FLD_Y = TP_CONTENT + LABEL_H;                    // 55
    private static final int TP_ROW2_Y = TP_FLD_Y + FIELD_H + GAP;              // 77

    // Ряды внутри нижней панели «маршрут»
    private static final int RT_CONTENT = ROUTE_Y + PANEL_HDR + 4;              // 147
    private static final int RT_FLD_Y = RT_CONTENT + LABEL_H;                    // 157
    private static final int RT_BTN_Y = RT_FLD_Y + FIELD_H + GAP;               // 179
    private static final int RT_LIST_Y = RT_BTN_Y + BTN_H + 6;                  // 201

    // X-смещения от guiLeft
    private static final int LEFT_X = PAD;                                       // 8
    private static final int RIGHT_X = PAD + PANEL_W + GAP;                      // 153
    private static final int ROUTE_X = PAD;                                      // 8

    // Координатные поля X/Y/Z (и WX/WY/WZ) — 3 поля, центрируются в своей панели
    private static final int FW = 40;
    private static final int FG = 5;
    private static final int COORD_TRIPLET_W = FW * 3 + FG * 2;                  // 130

    // Правая панель «параметры»: лейбл | поле | −− | ++
    private static final int A_LBL_X = RIGHT_X + 8;
    private static final int A_FLD_X = RIGHT_X + 36;
    private static final int A_FLD_W = 40;
    private static final int A_BTN_W = 22;
    private static final int A_BTN1_X = A_FLD_X + A_FLD_W + 6;
    private static final int A_BTN2_X = A_BTN1_X + A_BTN_W + 3;

    // Кнопки маршрута: 4 в ряд на всю ширину панели
    private static final int WP_BW = 66;
    private static final int WP_BG = 6;
    private static final int WP_B0 = ROUTE_X + (ROUTE_W - (WP_BW * 4 + WP_BG * 3)) / 2;

    // ── Палитра: тёмный тактический «планшет», единый янтарный акцент ─────────
    private static final int COL_BACKGROUND    = 0xF00D0F12;
    private static final int COL_BORDER        = 0xFF3A424C;
    private static final int COL_HEADER_BAR    = 0xFF14171B;
    private static final int COL_ACCENT        = 0xFFE0A030; // янтарь
    private static final int COL_TEXT_HEADER   = 0xFFECEFF2;
    private static final int COL_TEXT_LABEL    = 0xFF828A94;
    private static final int COL_TEXT_VALUE    = 0xFFE0A030; // значения полей — янтарь
    private static final int COL_PANEL         = 0xFF15181D;
    private static final int COL_PANEL_HEADER  = 0xFF1E232A;
    private static final int COL_PANEL_BORDER  = 0xFF2C333C;
    private static final int COL_BTN           = 0xFF232830;
    private static final int COL_BTN_HOVER     = 0xFF2F3640;
    private static final int COL_BTN_DISABLED  = 0xFF181B1F;
    private static final int COL_BTN_TEXT      = 0xFFD2D7DD;
    private static final int COL_BTN_TEXT_DIS  = 0xFF565C65;
    private static final int COL_WARNING       = 0xFFE0574B;
    private static final int COL_LAUNCH_BG     = 0xFF243A26;
    private static final int COL_LAUNCH_HOVER  = 0xFF2E4C31;
    private static final int COL_LAUNCH_BORDER = 0xFF5FD46A;
    private static final int COL_LAUNCH_TEXT   = 0xFF7FE889;

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

        // ═══ ЛЕВАЯ ПАНЕЛЬ: ЦЕЛЬ — X/Y/Z + «игрок»/«взгляд» ═══
        int coordX0 = guiLeft + LEFT_X + (PANEL_W - COORD_TRIPLET_W) / 2;
        int fldY = guiTop + TP_FLD_Y;
        this.xField = mkCoord(coordX0, fldY, FW, FIELD_H, "X", String.valueOf(initialX));
        this.yField = mkCoord(coordX0 + (FW + FG), fldY, FW, FIELD_H, "Y", String.valueOf(initialY));
        this.zField = mkCoord(coordX0 + (FW + FG) * 2, fldY, FW, FIELD_H, "Z", String.valueOf(initialZ));
        this.addRenderableWidget(this.xField);
        this.addRenderableWidget(this.yField);
        this.addRenderableWidget(this.zField);

        int lbw = (PANEL_W - 12 - GAP) / 2;    // две кнопки на всю ширину панели
        int lbx = guiLeft + LEFT_X + 6;
        int row2Y = guiTop + TP_ROW2_Y;
        this.addRenderableWidget(new MinimalButton(lbx, row2Y, lbw, BTN_H,
                Component.translatable("screen.wrbdrones.radio.player"), b -> useCurrentPosition()));
        this.addRenderableWidget(new MinimalButton(lbx + lbw + GAP, row2Y, lbw, BTN_H,
                Component.translatable("screen.wrbdrones.radio.look"), b -> useLookPosition()));

        // ═══ ПРАВАЯ ПАНЕЛЬ: ПАРАМЕТРЫ — высота / скорость ═══
        this.altitudeField = mkValue(guiLeft + A_FLD_X, fldY, A_FLD_W, FIELD_H, "ALT",
                String.valueOf((int) Mth.clamp(80, minAltitude, maxAltitude)), this::isValidInt);
        this.addRenderableWidget(this.altitudeField);
        this.addRenderableWidget(new MinimalButton(guiLeft + A_BTN1_X, fldY, A_BTN_W, FIELD_H,
                Component.literal("--"), b -> adjustAltitude(-50)));
        this.addRenderableWidget(new MinimalButton(guiLeft + A_BTN2_X, fldY, A_BTN_W, FIELD_H,
                Component.literal("++"), b -> adjustAltitude(50)));

        this.speedField = mkValue(guiLeft + A_FLD_X, row2Y, A_FLD_W, FIELD_H, "SPD",
                String.valueOf((int) Mth.clamp(180, minSpeed, maxSpeed)), this::isValidFloat);
        this.addRenderableWidget(this.speedField);
        this.addRenderableWidget(new MinimalButton(guiLeft + A_BTN1_X, row2Y, A_BTN_W, FIELD_H,
                Component.literal("-"), b -> adjustSpeed(-5)));
        this.addRenderableWidget(new MinimalButton(guiLeft + A_BTN2_X, row2Y, A_BTN_W, FIELD_H,
                Component.literal("+"), b -> adjustSpeed(5)));

        // ═══ РЯД ТУМБЛЕРОВ: манёвр уклонения | огибание рельефа ═══
        int togY = guiTop + TOG_Y;
        this.evasiveButton = new MinimalButton(guiLeft + LEFT_X, togY, PANEL_W, TOG_H,
                getEvasiveButtonText(), b -> toggleEvasive());
        this.terrainFollowButton = new MinimalButton(guiLeft + RIGHT_X, togY, PANEL_W, TOG_H,
                getTerrainFollowButtonText(), b -> toggleTerrainFollow());
        this.addRenderableWidget(this.evasiveButton);
        this.addRenderableWidget(this.terrainFollowButton);

        // ═══ НИЖНЯЯ ПАНЕЛЬ: МАРШРУТ ═══
        int wpX0 = guiLeft + ROUTE_X + (ROUTE_W - COORD_TRIPLET_W) / 2;
        int wpFldY = guiTop + RT_FLD_Y;
        this.wpXField = mkCoord(wpX0, wpFldY, FW, FIELD_H, "WX", "");
        this.wpYField = mkCoord(wpX0 + (FW + FG), wpFldY, FW, FIELD_H, "WY", "");
        this.wpZField = mkCoord(wpX0 + (FW + FG) * 2, wpFldY, FW, FIELD_H, "WZ", "");
        this.addRenderableWidget(this.wpXField);
        this.addRenderableWidget(this.wpYField);
        this.addRenderableWidget(this.wpZField);

        int wpBtnY = guiTop + RT_BTN_Y;
        int wpB0 = guiLeft + WP_B0;
        this.addWaypointButton = new MinimalButton(wpB0, wpBtnY, WP_BW, FIELD_H,
                Component.translatable("screen.wrbdrones.radio.wp_add"), b -> addWaypoint());
        this.addRenderableWidget(this.addWaypointButton);
        this.addRenderableWidget(new MinimalButton(wpB0 + (WP_BW + WP_BG), wpBtnY, WP_BW, FIELD_H,
                Component.translatable("screen.wrbdrones.radio.wp_look"), b -> useLookForWaypoint()));
        this.removeLastWaypointButton = new MinimalButton(wpB0 + (WP_BW + WP_BG) * 2, wpBtnY, WP_BW, FIELD_H,
                Component.translatable("screen.wrbdrones.radio.wp_remove_last"), b -> removeLastWaypoint());
        this.addRenderableWidget(this.removeLastWaypointButton);
        this.clearWaypointsButton = new MinimalButton(wpB0 + (WP_BW + WP_BG) * 3, wpBtnY, WP_BW, FIELD_H,
                Component.translatable("screen.wrbdrones.radio.wp_clear"), b -> clearWaypoints());
        this.addRenderableWidget(this.clearWaypointsButton);

        // ═══ ЗАПУСК ═══
        this.launchButton = new LaunchButton(guiLeft + LEFT_X, guiTop + LAUNCH_Y, ROUTE_W, BTN_H + 2,
                Component.translatable("screen.wrbdrones.radio.launch").withStyle(ChatFormatting.BOLD),
                b -> onLaunch());
        this.addRenderableWidget(this.launchButton);
    }

    /** Фабрика EditBox координаты (фильтр int, янтарный текст, бордер). */
    private EditBox mkCoord(int x, int y, int w, int h, String hint, String initial) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(hint));
        box.setMaxLength(10);
        box.setValue(initial);
        box.setFilter(this::isValidCoordinate);
        box.setTextColor(COL_TEXT_VALUE);
        box.setBordered(true);
        return box;
    }

    /** Фабрика EditBox параметра (высота/скорость) с заданным фильтром. */
    private EditBox mkValue(int x, int y, int w, int h, String hint, String initial,
            java.util.function.Predicate<String> filter) {
        EditBox box = new EditBox(this.font, x, y, w, h, Component.literal(hint));
        box.setMaxLength(4);
        box.setValue(initial);
        box.setFilter(filter);
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
            int newVal = Mth.clamp(current + delta, (int) minAltitude, (int) maxAltitude);
            altitudeField.setValue(String.valueOf(newVal));
        } catch (NumberFormatException e) {
            altitudeField.setValue(String.valueOf((int) Mth.clamp(80, minAltitude, maxAltitude)));
        }
    }

    private void adjustSpeed(int delta) {
        try {
            float current = Float.parseFloat(speedField.getValue());
            float newVal = Mth.clamp(current + delta, (float) minSpeed, (float) maxSpeed);
            speedField.setValue(String.valueOf((int) newVal));
        } catch (NumberFormatException e) {
            speedField.setValue(String.valueOf((int) Mth.clamp(180, minSpeed, maxSpeed)));
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

            if (calculateDistance() > maxDistance) {
                return; // предупреждение показывается в render
            }
            if (isSpeedInvalid() || isAltitudeInvalid()) {
                return;
            }

            speedKmh = Mth.clamp(speedKmh, (float) minSpeed, (float) maxSpeed);
            altitude = Mth.clamp(altitude, (float) minAltitude, (float) maxAltitude);

            // km/h -> blocks/tick (1 b/t = 72 km/h)
            float speedBlocksPerTick = speedKmh / 72.0f;

            PacketDistributor.sendToServer(
                    new LaunchShahedPacket(shahedEntityId, x, y, z, speedBlocksPerTick, altitude, evasiveMode,
                            terrainFollow, new ArrayList<>(waypoints)));
            this.onClose();
        } catch (NumberFormatException e) {
            // некорректные координаты — игнорируем
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
        float speedBlocksPerSec = (speedKmh / 72.0f) * 20.0f;
        if (speedBlocksPerSec <= 0)
            return 0;
        return dist / speedBlocksPerSec;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Затемнение/блюр ванильного фона, затем — вся «хромировка» планшета. Виджеты
        // (поля/кнопки) рисуются поверх стандартным циклом Screen.render.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);

        int guiLeft = (this.width - GUI_WIDTH) / 2;
        int guiTop = (this.height - GUI_HEIGHT) / 2;

        // 1. Фон + бордер «планшета»
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, COL_BACKGROUND);
        graphics.renderOutline(guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, COL_BORDER);

        // 2. Заголовок окна + янтарная акцент-линия под ним
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + HEADER_H, COL_HEADER_BAR);
        graphics.fill(guiLeft, guiTop + HEADER_H, guiLeft + GUI_WIDTH, guiTop + HEADER_H + 1, COL_ACCENT);
        graphics.drawCenteredString(this.font, Component.translatable("screen.wrbdrones.radio.header"),
                this.width / 2, guiTop + 7, COL_TEXT_HEADER);

        // 3. Панели
        drawPanel(graphics, guiLeft + LEFT_X, guiTop + PANEL_TOP_Y, PANEL_W, PANEL_TOP_H,
                Component.translatable("screen.wrbdrones.radio.target"));
        drawPanel(graphics, guiLeft + RIGHT_X, guiTop + PANEL_TOP_Y, PANEL_W, PANEL_TOP_H,
                Component.translatable("screen.wrbdrones.radio.params"));
        drawPanel(graphics, guiLeft + ROUTE_X, guiTop + ROUTE_Y, ROUTE_W, ROUTE_H,
                Component.translatable("screen.wrbdrones.radio.wp_list", waypoints.size(), maxWaypoints()));

        // 4. Лейблы X/Y/Z над полями
        int lblY = guiTop + TP_CONTENT;
        drawLabelCentered(graphics, "X", xField.getX() + xField.getWidth() / 2, lblY);
        drawLabelCentered(graphics, "Y", yField.getX() + yField.getWidth() / 2, lblY);
        drawLabelCentered(graphics, "Z", zField.getX() + zField.getWidth() / 2, lblY);

        // 5. Лейблы ВЫС/СКР слева от полей (правая панель), по центру строки поля
        graphics.drawString(this.font, Component.translatable("screen.wrbdrones.radio.alt"),
                guiLeft + A_LBL_X, altitudeField.getY() + 4, COL_TEXT_LABEL, false);
        graphics.drawString(this.font, Component.translatable("screen.wrbdrones.radio.spd"),
                guiLeft + A_LBL_X, speedField.getY() + 4, COL_TEXT_LABEL, false);

        // 6. Лейблы WX/WY/WZ над полями маршрута
        int wpLblY = guiTop + RT_CONTENT;
        drawLabelCentered(graphics, "WX", wpXField.getX() + wpXField.getWidth() / 2, wpLblY);
        drawLabelCentered(graphics, "WY", wpYField.getX() + wpYField.getWidth() / 2, wpLblY);
        drawLabelCentered(graphics, "WZ", wpZField.getX() + wpZField.getWidth() / 2, wpLblY);

        // 7. Список маршрута (под кнопками)
        int rowY = guiTop + RT_LIST_Y;
        int maxRows = Math.min(waypoints.size(), 3);
        for (int i = 0; i < maxRows; i++) {
            int[] wp = waypoints.get(i);
            String row = String.format("§7%d. §f%d %d %d", i + 1, wp[0], wp[1], wp[2]);
            graphics.drawCenteredString(this.font, row, this.width / 2, rowY + i * 9, COL_TEXT_HEADER);
        }
        if (waypoints.size() > 3) {
            graphics.drawCenteredString(this.font, "§7+" + (waypoints.size() - 3), this.width / 2,
                    rowY + 3 * 9, COL_TEXT_LABEL);
        }

        // 8. Статус над кнопкой запуска
        int statusY = guiTop + STATUS_Y;
        double distance = calculateDistance();
        if (distance > maxDistance) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.wrbdrones.radio.warning.too_far", (int) maxDistance),
                    this.width / 2, statusY, COL_WARNING);
        } else if (isSpeedInvalid()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.wrbdrones.radio.warning.invalid_speed", (int) minSpeed, (int) maxSpeed),
                    this.width / 2, statusY, COL_WARNING);
        } else if (isAltitudeInvalid()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.wrbdrones.radio.warning.invalid_alt", (int) minAltitude, (int) maxAltitude),
                    this.width / 2, statusY, COL_WARNING);
        } else {
            String distStr = Component.translatable("screen.wrbdrones.radio.dist",
                    String.format("%.1f", distance)).getString();
            String etaStr = Component.translatable("screen.wrbdrones.radio.eta",
                    String.format("%.2f", calculateETA())).getString();
            graphics.drawCenteredString(this.font, distStr + "   " + etaStr, this.width / 2, statusY, COL_TEXT_LABEL);
        }
    }

    private boolean isSpeedInvalid() {
        float val = parseFloat(speedField.getValue(), 0f);
        return val < minSpeed || val > maxSpeed;
    }

    private boolean isAltitudeInvalid() {
        float val = parseFloat(altitudeField.getValue(), 0f);
        return val < minAltitude || val > maxAltitude;
    }

    private void drawLabelCentered(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawCenteredString(this.font, text, x, y, COL_TEXT_LABEL);
    }

    /** Панель «планшета»: блок с тонким бордером и заголовком-вкладкой сверху. */
    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h, Component title) {
        graphics.fill(x, y, x + w, y + h, COL_PANEL);
        graphics.renderOutline(x, y, w, h, COL_PANEL_BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + PANEL_HDR, COL_PANEL_HEADER);
        graphics.fill(x, y + PANEL_HDR, x + w, y + PANEL_HDR + 1, COL_PANEL_BORDER);
        graphics.drawCenteredString(this.font, title, x + w / 2, y + 3, COL_TEXT_LABEL);
    }

    /** Плоская кнопка в стиле консоли. */
    private class MinimalButton extends Button {
        MinimalButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int bg = this.active ? (this.isHoveredOrFocused() ? COL_BTN_HOVER : COL_BTN) : COL_BTN_DISABLED;
            int border = this.isHoveredOrFocused() && this.active ? COL_ACCENT : COL_PANEL_BORDER;
            int text = this.active ? COL_BTN_TEXT : COL_BTN_TEXT_DIS;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);
            graphics.renderOutline(getX(), getY(), width, height, border);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, text);
        }
    }

    /** Главная кнопка «ЗАПУСК» — зелёная рамка/текст, чтобы выделяться. */
    private class LaunchButton extends Button {
        LaunchButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int bg = this.isHoveredOrFocused() ? COL_LAUNCH_HOVER : COL_LAUNCH_BG;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);
            graphics.renderOutline(getX(), getY(), width, height, COL_LAUNCH_BORDER);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2,
                    getY() + (height - 8) / 2, COL_LAUNCH_TEXT);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
