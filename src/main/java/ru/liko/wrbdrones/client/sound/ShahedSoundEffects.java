package ru.liko.wrbdrones.client.sound;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Pure functions for computing realistic audio effect parameters.
 * maxAudibleDistance from config: 0 = unlimited (no attenuation by distance).
 */
@OnlyIn(Dist.CLIENT)
public final class ShahedSoundEffects {

    private static final double DEFAULT_MIN_ATTEN_RATIO = 0.075;
    private static final float ABSORPTION_COEFF = 1.0f;

    // ~340 m/s expressed in blocks/tick (20 ticks/sec, 1 block ≈ 1 meter)
    private static final double SPEED_OF_SOUND = 17.0;
    private static final float MIN_DOPPLER_PITCH = 0.5f;
    private static final float MAX_DOPPLER_PITCH = 2.0f;

    private static final double ALTITUDE_THRESHOLD = 50.0;
    private static final double ALTITUDE_MAX = 200.0;
    private static final float ALTITUDE_MIN_GAINHF = 0.3f;

    private ShahedSoundEffects() {
    }

    /**
     * Computes the low-pass gainHF based on distance.
     *
     * @param maxAudibleDistance 0 = unlimited (return 1.0)
     */
    public static float computeDistanceGainHF(double distance, double maxAudibleDistance) {
        if (maxAudibleDistance <= 0) {
            return 1.0f;
        }
        double minAtten = maxAudibleDistance * DEFAULT_MIN_ATTEN_RATIO;
        double attenRange = maxAudibleDistance - minAtten;
        if (attenRange <= 0) attenRange = 1.0;

        if (distance <= minAtten) {
            return 1.0f;
        }
        if (distance >= maxAudibleDistance) {
            return 0.03f;
        }
        double normalized = (distance - minAtten) / attenRange;
        return (float) Math.exp(-ABSORPTION_COEFF * normalized);
    }

    /**
     * Computes the Doppler pitch multiplier based on relative radial velocity.
     *
     * @param dronePos      current drone position
     * @param droneVelocity drone velocity (blocks/tick), typically deltaMovement
     * @param playerPos     listener (player/camera) position
     * @return pitch multiplier (>1 approaching, <1 receding)
     */
    public static float computeDopplerPitch(Vec3 dronePos, Vec3 droneVelocity, Vec3 playerPos) {
        Vec3 toPlayer = playerPos.subtract(dronePos);
        double dist = toPlayer.length();
        if (dist < 0.01) {
            return 1.0f;
        }

        Vec3 dirToPlayer = toPlayer.scale(1.0 / dist);
        // Radial velocity: positive = moving towards player, negative = away
        double radialVelocity = droneVelocity.dot(dirToPlayer);

        // Doppler: pitch = Cs / (Cs - Vr)
        // Vr positive (toward player) → denominator smaller → pitch higher
        double denominator = SPEED_OF_SOUND - radialVelocity;
        if (denominator <= 0.1) {
            return MAX_DOPPLER_PITCH;
        }

        float dopplerPitch = (float) (SPEED_OF_SOUND / denominator);
        return Mth.clamp(dopplerPitch, MIN_DOPPLER_PITCH, MAX_DOPPLER_PITCH);
    }

    /**
     * Computes additional high-frequency attenuation based on altitude difference.
     * When the drone is high above the player, sound becomes more muffled.
     *
     * @return gainHF multiplier (0.3 - 1.0)
     */
    public static float computeAltitudeGainHF(double droneY, double playerY) {
        double altDiff = Math.abs(droneY - playerY);
        if (altDiff <= ALTITUDE_THRESHOLD) {
            return 1.0f;
        }
        if (altDiff >= ALTITUDE_MAX) {
            return ALTITUDE_MIN_GAINHF;
        }
        double normalized = (altDiff - ALTITUDE_THRESHOLD) / (ALTITUDE_MAX - ALTITUDE_THRESHOLD);
        return Mth.lerp((float) normalized, 1.0f, ALTITUDE_MIN_GAINHF);
    }

    /**
     * Combines distance and altitude gainHF into a single filter parameter.
     *
     * @param maxAudibleDistance 0 = unlimited
     */
    public static float computeCombinedGainHF(double distance, double droneY, double playerY, double maxAudibleDistance) {
        float distGainHF = computeDistanceGainHF(distance, maxAudibleDistance);
        float altGainHF = computeAltitudeGainHF(droneY, playerY);
        return distGainHF * altGainHF;
    }

    /**
     * Computes volume attenuation factor by distance (0 = silent, 1 = full volume).
     * Uses inverse-distance (1/r) with a smooth edge fade to zero at max distance.
     *
     * @param maxAudibleDistance 0 = unlimited (return 1.0)
     */
    public static float computeDistanceVolumeFactor(double distance, double maxAudibleDistance) {
        if (maxAudibleDistance <= 0) {
            return 1.0f;
        }
        double minAtten = maxAudibleDistance * DEFAULT_MIN_ATTEN_RATIO;
        double attenRange = maxAudibleDistance - minAtten;
        if (attenRange <= 0) attenRange = 1.0;

        if (distance <= minAtten) {
            return 1.0f;
        }
        if (distance >= maxAudibleDistance) {
            return 0.0f;
        }
        float invDist = (float) (minAtten / distance);
        double normalized = (distance - minAtten) / attenRange;
        float edgeFade = (float) (1.0 - normalized * normalized);
        return invDist * edgeFade;
    }
}
