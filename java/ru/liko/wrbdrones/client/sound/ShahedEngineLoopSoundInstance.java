package ru.liko.wrbdrones.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ShahedEngineLoopSoundInstance extends AbstractTickableSoundInstance {
    private static final int MAX_FADE_TICKS = 60;
    private static final long TIMEOUT_TICKS = 20;

    private static final double MIN_ATTEN_DISTANCE = 75.0;
    private static final double MAX_ATTEN_DISTANCE = 350.0;
    private static final double ATTEN_RANGE = MAX_ATTEN_DISTANCE - MIN_ATTEN_DISTANCE;

    private long lastUpdateTick = -1;
    private boolean dying = false;
    private int fadeTicks = 0;

    private float targetVolume = 0.0f;
    private float targetPitch = 1.0f;
    private double droneSpeed = 0.0;

    public ShahedEngineLoopSoundInstance(final SoundEvent sound) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0f;
        this.pitch = 1.0f;
    }

    public void update(
        final double x,
        final double y,
        final double z,
        final float engineMix,
        final float diveFactor,
        final float climbFactor,
        final float speedFactor,
        final float turnFactor,
        final long gameTick
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastUpdateTick = gameTick;
        this.dying = false;

        final float mix = Mth.clamp(engineMix, 0.0f, 1.0f);

        final float flightVolumeMult = 1.0f + diveFactor * 0.9f + speedFactor * 0.35f + turnFactor * 0.25f;
        final float flightPitchOffset = diveFactor * 0.8f - climbFactor * 0.2f + turnFactor * 0.4f;

        final float baseVol = 1.5f + 2.5f * mix;
        final float basePitch = 0.7f + 0.25f * mix;

        this.targetVolume = Mth.clamp(baseVol * flightVolumeMult, 0.0f, 8.0f);
        
        final float speedPitchMult = Mth.clamp(speedFactor * 0.3f, 0.0f, 0.5f);
        this.targetPitch = Mth.clamp(basePitch + flightPitchOffset + speedPitchMult, 0.2f, 2.0f);
    }

    public void requestFadeOut() {
        this.dying = true;
    }

    public void forceStop() {
        stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.isPaused()) {
            stop();
            return;
        }

        final long now = mc.level.getGameTime();
        if (lastUpdateTick >= 0 && now - lastUpdateTick > TIMEOUT_TICKS) {
            dying = true;
        }

        if (dying) {
            if (fadeTicks > 0) {
                fadeTicks--;
            } else {
                stop();
                return;
            }
        } else if (fadeTicks < MAX_FADE_TICKS) {
            fadeTicks++;
        }

        float extraDistanceFactor = 1.0f;
        if (mc.getCameraEntity() != null) {
            final double distSqr = mc.getCameraEntity().distanceToSqr(new Vec3(x, y, z));
            final double distance = Math.sqrt(distSqr);
            
            if (distance <= MIN_ATTEN_DISTANCE) {
                extraDistanceFactor = 1.0f;
            } else if (distance >= MAX_ATTEN_DISTANCE) {
                extraDistanceFactor = 0.0f;
            } else {
                final double normalizedDist = (distance - MIN_ATTEN_DISTANCE) / ATTEN_RANGE;
                // Комбинируем спад 1/dist и линейное затухание к нулю, чтобы звук утихал плавно,
                // даже если базовая громкость > 1.0
                final double physFactor = MIN_ATTEN_DISTANCE / distance;
                final double cutOffFactor = 1.0 - normalizedDist;
                extraDistanceFactor = (float) (physFactor * cutOffFactor);
            }
        }

        final float fadeMul = fadeTicks / (float) MAX_FADE_TICKS;
        final float desiredVol = targetVolume * fadeMul * extraDistanceFactor;

        this.volume = Mth.lerp(0.25f, this.volume, desiredVol);
        this.pitch = Mth.lerp(0.25f, this.pitch, targetPitch);

        if (this.volume <= 0.0005f && dying) {
            stop();
        }
    }
}
