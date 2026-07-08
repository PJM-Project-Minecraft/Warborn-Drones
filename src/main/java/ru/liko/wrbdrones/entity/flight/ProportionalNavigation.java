package ru.liko.wrbdrones.entity.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Пропорциональное наведение (Proportional Navigation, PNG) — stateless-хелпер
 * для терминальной фазы. Команда бокового ускорения a = N · V_closing · ω_LOS,
 * где N — навигационная постоянная (обычно 3–4), V_closing — скорость сближения
 * вдоль линии визирования (LOS), ω_LOS — угловая скорость LOS в горизонтальной
 * плоскости. Результат — желаемый темп разворота по курсу [град/тик] в той же
 * знаковой конвенции, что и {@code autoYawChange} в контроллере крена Shahed
 * (положительное = прибавка к yRot), поэтому напрямую ложится в
 * {@link FlightDemand#yawDiff()} через деление на gain курсового режима.
 *
 * <p>Все скорости — блоках/тик, время — в тиках, углы — в градусах MC-yaw.
 * Цель предполагается статической (для движущейся цели relVel = targetVel −
 * craftVel — расширение тривиально, но Shahed бьёт по координате).
 *
 * <p>Единственный носитель PNG-формулы (см. CLAUDE.md: single source of truth).
 */
public final class ProportionalNavigation {

    private ProportionalNavigation() {
    }

    /**
     * Один шаг PNG в горизонтальной плоскости.
     *
     * @param craftPos       позиция снаряда (используются X/Z)
     * @param craftVel       скорость снаряда (X/Z), бл/тик — обычно getDeltaMovement()
     * @param targetPos      позиция цели
     * @param prevLosYawDeg  угол LOS в предыдущий тик (MC-yaw, град); NaN на первом вызове
     * @param prevRange      дальность в предыдущий тик (бл); NaN на первом вызове
     * @param navConstant    навигационная постоянная N
     * @param maxTurnRateDeg макс. темп разворота, град/тик (ограничение команды)
     * @return шаг: команда темпа разворота + обновлённые LOS-состояния для хранения
     */
    public static PngStep stepHorizontal(
            Vec3 craftPos, Vec3 craftVel, Vec3 targetPos,
            double prevLosYawDeg, double prevRange,
            float navConstant, float maxTurnRateDeg) {

        double dx = targetPos.x - craftPos.x;
        double dz = targetPos.z - craftPos.z;
        double rangeH = Math.sqrt(dx * dx + dz * dz);
        // MC-yaw конвенция: atan2(dz, dx)·180/π − 90 (как desiredYaw в курсовом режиме).
        double losYawDeg = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;

        // На первом тике или вырожденной геометрии производной нет — нейтральная команда.
        if (rangeH < 1.0e-4
                || Double.isNaN(prevLosYawDeg) || Double.isNaN(prevRange)
                || prevRange < 1.0e-4) {
            return new PngStep(0.0f, losYawDeg, rangeH);
        }

        // Скорость сближения по дальности (positive = приближаемся). Для статической
        // цели V_c = prevRange − range (за тик).
        double vClosing = prevRange - rangeH;
        if (vClosing <= 0.0) {
            // Не сближаемся (прошли цель / орбита) — PNG не определён, не командуем,
            // пусть работает контактный/неконтактный взрыватель.
            return new PngStep(0.0f, losYawDeg, rangeH);
        }

        double speedH = Math.sqrt(craftVel.x * craftVel.x + craftVel.z * craftVel.z);
        if (speedH < 1.0e-4) {
            return new PngStep(0.0f, losYawDeg, rangeH);
        }

        double losRateDeg = Mth.wrapDegrees(losYawDeg - prevLosYawDeg); // град/тик
        double aCmd = navConstant * vClosing * losRateDeg;              // бл·град/тик²
        double cmdHeadingRateDeg = aCmd / speedH;                       // град/тик
        float clamped = Mth.clamp((float) cmdHeadingRateDeg, -maxTurnRateDeg, maxTurnRateDeg);

        return new PngStep(clamped, losYawDeg, rangeH);
    }

    /** Результат шага PNG + обновлённые LOS-состояния для хранения на сущности. */
    public record PngStep(float cmdHeadingRateDeg, double losYawDeg, double rangeH) {
    }
}
