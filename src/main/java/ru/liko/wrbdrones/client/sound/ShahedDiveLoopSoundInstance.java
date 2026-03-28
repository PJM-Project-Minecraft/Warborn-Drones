package ru.liko.wrbdrones.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ShahedDiveLoopSoundInstance extends AbstractTickableSoundInstance {
    private static final int MAX_FADE_TICKS = 50;
    private static final long TIMEOUT_TICKS = 15;

    private static final float GAINH_LERP = 0.15f;
    private static final float DOPPLER_LERP = 0.2f;

    private long lastUpdateTick = -1;
    private boolean dying = false;
    private int fadeTicks = 0;

    private float targetVolume = 0.0f;
    private float targetPitch = 0.9f;

    private float targetDopplerPitch = 1.0f;
    private float currentDopplerPitch = 1.0f;

    private float targetGainHF = 1.0f;
    private float currentGainHF = 1.0f;

    private final double maxAudibleDistance;

    public ShahedDiveLoopSoundInstance(final SoundEvent sound, final double maxAudibleDistance) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0f;
        this.pitch = 0.9f;
        this.maxAudibleDistance = maxAudibleDistance;
    }

    public void update(
            final double x,
            final double y,
            final double z,
            final float diveIntensity,
            final float speedFactor,
            final float dopplerPitch,
            final float gainHF,
            final long gameTick) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastUpdateTick = gameTick;
        this.dying = false;

        this.targetDopplerPitch = dopplerPitch;
        this.targetGainHF = gainHF;

        final float dive = Mth.clamp(diveIntensity, 0.0f, 1.6f);
        final float speed = Mth.clamp(speedFactor, 0.0f, 1.0f);

        this.targetVolume = Mth.clamp(2.0f + dive * 2.5f + speed * 0.5f, 0.0f, 8.0f);

        final float speedPitchMult = Mth.clamp(speed * 0.3f, 0.0f, 0.4f);
        this.targetPitch = Mth.clamp(0.9f + dive * 0.5f + speedPitchMult, 0.2f, 2.0f);
    }

    public void requestFadeOut() {
        this.dying = true;
    }

    public void keepAlive(final long gameTick) {
        this.lastUpdateTick = gameTick;
    }

    public void extrapolate(final double newX, final double newY, final double newZ,
                            final float dopplerPitch, final float gainHF, final long gameTick) {
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        this.targetDopplerPitch = dopplerPitch;
        this.targetGainHF = gainHF;
        this.lastUpdateTick = gameTick;
    }

    public void forceStop() {
        removeFilter();
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
            removeFilter();
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
                removeFilter();
                stop();
                return;
            }
            targetGainHF = 1.0f;
            targetDopplerPitch = 1.0f;
        } else if (fadeTicks < MAX_FADE_TICKS) {
            fadeTicks++;
        }

        currentDopplerPitch = Mth.lerp(DOPPLER_LERP, currentDopplerPitch, targetDopplerPitch);
        currentGainHF = Mth.lerp(GAINH_LERP, currentGainHF, targetGainHF);

        float distanceVolumeFactor = 1.0f;
        if (mc.getCameraEntity() != null) {
            double distance = mc.getCameraEntity().position().distanceTo(new Vec3(x, y, z));
            distanceVolumeFactor = ShahedSoundEffects.computeDistanceVolumeFactor(distance, maxAudibleDistance);
        }

        final float fadeMul = fadeTicks / (float) MAX_FADE_TICKS;
        final float desiredVol = targetVolume * fadeMul * distanceVolumeFactor;

        this.volume = Mth.lerp(0.25f, this.volume, desiredVol);
        this.pitch = Mth.lerp(0.25f, this.pitch, targetPitch * currentDopplerPitch);

        applyFilter();

        if (this.volume <= 0.0005f && dying) {
            removeFilter();
            stop();
        }
    }

    private void applyFilter() {
        if (!ShahedOpenALFilters.isAvailable()) return;
        ShahedOpenALFilters.applyFilterForInstance(this, 1.0f, currentGainHF);
    }

    private void removeFilter() {
        if (!ShahedOpenALFilters.isAvailable()) return;
        ShahedOpenALFilters.removeFilterForInstance(this);
    }
}
