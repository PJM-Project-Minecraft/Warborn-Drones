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
public final class ShahedEngineLoopSoundInstance extends AbstractTickableSoundInstance {
    private static final int MAX_FADE_TICKS = 60;
    private static final long TIMEOUT_TICKS = 20;

    private static final float GAINH_LERP = 0.15f;
    private static final float DOPPLER_LERP = 0.2f;

    // ── Органический «рокот» поршневого двигателя ───────────────────────────
    // Shahed-136 несёт поршневой мотор с толкающим винтом — узнаваемый «мопедный»
    // стрекот. Чистый луп без модуляции звучит синтетически, поэтому накладываем
    // лёгкое тремоло (амплитуда) и девиацию тона (частота) из несоизмеримых гармоник:
    // нет слышимой периодичности, на малых оборотах мотор «троит» сильнее, на
    // крейсере звук ровнее. Частоты заданы в рад/тик (20 тиков/сек): 2π·f/20.
    private static final double TREMOLO_RATE_A = 2.0 * Math.PI * 3.1 / 20.0;
    private static final double TREMOLO_RATE_B = 2.0 * Math.PI * 5.7 / 20.0;
    private static final double WARBLE_RATE = 2.0 * Math.PI * 4.3 / 20.0;
    private static final float TREMOLO_DEPTH = 0.09f; // ±9 % громкости на полном «рокоте»
    private static final float WARBLE_DEPTH = 0.012f; // ±1.2 % частоты
    private static final float ROUGHNESS_BASE = 0.5f; // постоянная составляющая
    private static final float ROUGHNESS_IDLE = 0.5f; // добавка на малых оборотах (итог 0.5..1.0)

    private long lastUpdateTick = -1;
    private boolean dying = false;
    private int fadeTicks = 0;

    private float targetVolume = 0.0f;
    private float targetPitch = 1.0f;

    private float targetDopplerPitch = 1.0f;
    private float currentDopplerPitch = 1.0f;

    private float targetGainHF = 1.0f;
    private float currentGainHF = 1.0f;

    private float engineLoad = 0.0f;
    private float smoothedVolume = 0.0f;
    private float smoothedPitch = 1.0f;
    private final float phaseOffset;

    private final double maxAudibleDistance;

    public ShahedEngineLoopSoundInstance(final SoundEvent sound, final double maxAudibleDistance) {
        super(sound, SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0f;
        this.pitch = 1.0f;
        this.maxAudibleDistance = maxAudibleDistance;
        // Случайный сдвиг фазы «рокота», чтобы несколько дронов в воздухе не стрекотали
        // строго в унисон.
        this.phaseOffset = this.random.nextFloat() * Mth.TWO_PI;
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

        final float mix = Mth.clamp(engineMix, 0.0f, 1.0f);
        this.engineLoad = mix;

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

        // Базовые сглаженные значения — инерция двигателя: обороты (и тон) не меняются
        // мгновенно, мотор «раскручивается» и «сбрасывает» плавно.
        smoothedVolume = Mth.lerp(0.25f, smoothedVolume, desiredVol);
        smoothedPitch = Mth.lerp(0.25f, smoothedPitch, targetPitch * currentDopplerPitch);

        // Наложение «рокота» поверх сглаженной базы. Считаем по игровому времени (+ сдвиг
        // фазы инстанса), амплитуда выше на малых оборотах и спадает к крейсеру.
        final float roughAmt = ROUGHNESS_BASE + ROUGHNESS_IDLE * (1.0f - engineLoad);
        final double phase = now + phaseOffset;
        final float tremolo = (float) (Math.sin(phase * TREMOLO_RATE_A) * 0.6
                + Math.sin(phase * TREMOLO_RATE_B + 1.3) * 0.4);
        final float warble = (float) Math.sin(phase * WARBLE_RATE + 0.7);

        this.volume = Math.max(0.0f, smoothedVolume * (1.0f + tremolo * roughAmt * TREMOLO_DEPTH));
        this.pitch = Mth.clamp(smoothedPitch * (1.0f + warble * roughAmt * WARBLE_DEPTH), 0.2f, 2.0f);

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
