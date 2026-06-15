package ru.liko.wrbdrones.entity.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Чистое (stateless) ядро аэродинамики неподвижного крыла — единственный носитель
 * энергетической формулы airspeed. Используется и Lancet, и Shahed (см. CLAUDE.md:
 * «single source of truth, do not parallel it elsewhere»).
 */
public final class FixedWingDynamics {

    private FixedWingDynamics() {
    }

    /**
     * Интеграция воздушной скорости за тик БЕЗ клампа:
     * airspeed += thrust(throttle) + pitchGravity·sin(pitch) − drag·airspeed².
     * Клампинг к диапазону [0, diveMax] и спец-логику оставляем вызывающему коду.
     *
     * @param pitchDeg тангаж в градусах (вниз положительный)
     * @param throttle газ [0..1]
     */
    public static float integrateAirspeed(float airspeed, float pitchDeg, float throttle, AircraftProfile p) {
        float pitchRad = (float) Math.toRadians(pitchDeg);
        float gravComp = p.pitchGravity() * Mth.sin(pitchRad);
        float thrustAccel = p.thrustIdle() + (p.thrustFull() - p.thrustIdle()) * throttle;
        float dragAccel = p.dragCoef() * airspeed * airspeed;
        return airspeed + thrustAccel + gravComp - dragAccel;
    }

    /** Дефицит сваливания [0..1]: 0 при airspeed ≥ stall, иначе 1 − airspeed/stall. */
    public static float stallDeficit(float airspeed, float stallSpeed) {
        if (airspeed >= stallSpeed) {
            return 0.0f;
        }
        return 1.0f - airspeed / stallSpeed;
    }

    /** Связь крен→рыскание: bankYawFactor·(v/cruise)², квадратично от скорости. */
    public static float bankFactor(float airspeed, AircraftProfile p) {
        float speedFrac = Mth.clamp(airspeed / Math.max(p.cruiseSpeed(), 0.01f), 0.3f, 1.6f);
        return p.bankYawFactor() * speedFrac * speedFrac;
    }

    /** Вектор движения из ориентации и airspeed + проседание Y при сваливании (квадратично). */
    public static Vec3 assembleMotion(float pitchDeg, float yawDeg, float airspeed,
                                      float stallDeficit, AircraftProfile p) {
        Vec3 motion = Vec3.directionFromRotation(pitchDeg, yawDeg).scale(airspeed);
        if (stallDeficit > 0.0f) {
            motion = motion.add(0.0, -p.stallLiftLoss() * stallDeficit * stallDeficit, 0.0);
        }
        return motion;
    }
}
