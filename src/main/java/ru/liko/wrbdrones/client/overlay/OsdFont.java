package ru.liko.wrbdrones.client.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import ru.liko.wrbdrones.Wrbdrones;

/**
 * Рендерер «железного» OSD-шрифта дронов на базе знакогенератора MAX7456
 * (файл impact.mcm, сконвертированный в атлас {@code textures/overlay/osd_font.png}).
 *
 * <p>Атлас — сетка 16×16 глифов, каждый глиф {@value #GLYPH_W}×{@value #GLYPH_H} px.
 * Глифы белые с чёрным контуром (классический вид аналогового OSD), фон прозрачный.
 * Индекс глифа совпадает с ASCII-кодом для печатных символов (цифры на 0x30,
 * заглавные буквы на 0x41 и т.д.), поэтому строки рендерятся напрямую.</p>
 *
 * <p>Шрифт монохромный по своей природе. Тонирование ({@code color}) применяется
 * ко всему глифу через {@link RenderSystem#setShaderColor}, поэтому чёрный контур
 * тоже окрашивается — для аутентичного вида используйте белый цвет.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class OsdFont {

    public static final ResourceLocation ATLAS = Wrbdrones.loc("textures/overlay/osd_font.png");

    /** Размер одного глифа в атласе, px. */
    public static final int GLYPH_W = 12;
    public static final int GLYPH_H = 18;

    private static final int GRID = 16;
    private static final int ATLAS_W = GRID * GLYPH_W; // 192
    private static final int ATLAS_H = GRID * GLYPH_H; // 288

    // --- Коды иконок в знакогенераторе MAX7456 (impact.mcm) ---
    /** Антенна-столбики (уровень сигнала/RSSI). */
    public static final char SIGNAL = 0x01;
    /** Домик. */
    public static final char HOUSE = 0x05;
    /** Пин «дом» с буквой H (точка возврата/дистанция). */
    public static final char HOME = 0x11;
    /** Иконка спутника — состоит из двух половин 0x1E + 0x1F. */
    public static final char SAT_L = 0x1E;
    public static final char SAT_R = 0x1F;
    /** Полная батарея (далее по убыванию до 0x97 — пустая). */
    public static final char BATTERY = 0x90;
    /** Единица скорости «км/ч». */
    public static final char KMH = 0x9E;
    /** Единица скорости «м/с». */
    public static final char MS = 0x9F;
    /** Начало кольца из 16 стрелок направления (0x60..0x6F), шаг 22.5°. */
    public static final char ARROW_BASE = 0x60;
    /** Тонкое перекрестие «+» (центральный прицел). */
    public static final char CROSS = 0x0B;

    /** Готовая двухсимвольная иконка спутника. */
    public static final String SAT = "" + SAT_L + SAT_R;

    /**
     * Возвращает глиф стрелки, указывающей на мировую точку {@code homePos}
     * относительно текущей камеры (для FPV/Lancet/Mavic — это камера дрона).
     *
     * <p>Направление считается через базис камеры (право/верх), без зависимости от
     * конвенций MC-yaw. Экранный пеленг β отсчитывается по часовой от «вверх»;
     * глиф берётся из кольца 0x60..0x6F (0x60 — вниз, 0x68 — вверх, 0x64 — влево,
     * 0x6C — вправо, шаг 22.5° против часовой).</p>
     */
    public static char homeArrow(Vec3 homePos) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 toHome = homePos.subtract(cam.getPosition());
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        double upDot = toHome.x * up.x() + toHome.y * up.y() + toHome.z * up.z();
        double leftDot = toHome.x * left.x() + toHome.y * left.y() + toHome.z * left.z();
        // right = -left; β = atan2(вправо, вверх): вправо→90°, вверх→0°
        float bearing = (float) (Mth.atan2(-leftDot, upDot) * Mth.RAD_TO_DEG);
        int idx = Math.floorMod(Math.round(bearing / 22.5f) + 8, 16);
        return (char) (ARROW_BASE + idx);
    }

    private OsdFont() {
    }

    /** Ширина строки в пикселях при заданном масштабе. */
    public static int width(String text, float scale) {
        return Math.round(text.length() * GLYPH_W * scale);
    }

    /** Высота глифа в пикселях при заданном масштабе. */
    public static int height(float scale) {
        return Math.round(GLYPH_H * scale);
    }

    /** Рисует одиночный глиф по его индексу (0..255) в левом верхнем углу (x, y). */
    public static void drawGlyph(GuiGraphics gfx, int glyph, int x, int y, float scale) {
        glyph &= 0xFF;
        int srcX = (glyph % GRID) * GLYPH_W;
        int srcY = (glyph / GRID) * GLYPH_H;
        int destW = Math.round(GLYPH_W * scale);
        int destH = Math.round(GLYPH_H * scale);
        gfx.blit(ATLAS, x, y, destW, destH, srcX, srcY, GLYPH_W, GLYPH_H, ATLAS_W, ATLAS_H);
    }

    /** Рисует строку белым цветом от левого края (x, y — верхний левый угол). */
    public static void drawString(GuiGraphics gfx, String text, int x, int y, float scale) {
        drawString(gfx, text, x, y, scale, 0xFFFFFFFF);
    }

    /**
     * Рисует строку OSD-шрифтом. {@code color} — ARGB-тон, умножается на глиф
     * (контур тоже окрасится; для классического OSD передавайте белый).
     */
    public static void drawString(GuiGraphics gfx, String text, int x, int y, float scale, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        if (a == 0.0f) {
            a = 1.0f; // цвет без альфы трактуем как непрозрачный
        }

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);

        int step = Math.round(GLYPH_W * scale);
        int cursor = x;
        for (int i = 0; i < text.length(); i++) {
            drawGlyph(gfx, text.charAt(i), cursor, y, scale);
            cursor += step;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** Рисует строку с центрированием по горизонтали относительно (cx, y). */
    public static void drawCentered(GuiGraphics gfx, String text, int cx, int y, float scale, int color) {
        drawString(gfx, text, cx - width(text, scale) / 2, y, scale, color);
    }

    /** Рисует строку с правым выравниванием (правый край в точке rightX). */
    public static void drawRight(GuiGraphics gfx, String text, int rightX, int y, float scale, int color) {
        drawString(gfx, text, rightX - width(text, scale), y, scale, color);
    }
}
