package ru.liko.wrbdrones.entity.flight;

/**
 * Параметры конкретного летательного аппарата для {@link FixedWingDynamics}.
 * Все скорости — в блоках/тик (1/72 ≈ 1 км/ч).
 *
 * @param thrustIdle    ускорение тяги на нулевом газу
 * @param thrustFull    ускорение тяги на полном газу
 * @param dragCoef      коэффициент квадратичного лобового сопротивления
 * @param pitchGravity  амплитуда гравитационной составляющей по тангажу (энергообмен)
 * @param stallSpeed    скорость сваливания
 * @param cruiseSpeed   крейсерская скорость (нормировка связи крен→рыскание)
 * @param diveMaxSpeed  потолок скорости в пикировании
 * @param stallLiftLoss потеря подъёмной силы (проседание Y) при сваливании
 * @param bankYawFactor базовый коэффициент связи крен→рыскание
 */
public record AircraftProfile(
        float thrustIdle, float thrustFull, float dragCoef, float pitchGravity,
        float stallSpeed, float cruiseSpeed, float diveMaxSpeed,
        float stallLiftLoss, float bankYawFactor) {
}
