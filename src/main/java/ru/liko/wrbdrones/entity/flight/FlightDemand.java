package ru.liko.wrbdrones.entity.flight;

/**
 * «Хотелка» курсового автопилота/пилота для аэродинамической модели неподвижного крыла.
 *
 * @param hasAutopilot активен ли автопилот (false — ручной/нейтральный режим)
 * @param pitch        желаемый тангаж, град. (вниз положительный — как в movement-коде)
 * @param yawDiff      рассогласование по курсу к цели, град. (wrapDegrees)
 * @param turnRate     максимальный темп разворота за тик, град.
 */
public record FlightDemand(boolean hasAutopilot, float pitch, float yawDiff, float turnRate) {
    /** Нейтральная команда: автопилота нет. */
    public static FlightDemand manual() {
        return new FlightDemand(false, 0.0f, 0.0f, 0.0f);
    }
}
