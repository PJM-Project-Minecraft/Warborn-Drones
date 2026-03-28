package ru.liko.wrbdrones.client.sound;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import ru.liko.wrbdrones.mixin.client.ChannelAccessor;
import ru.liko.wrbdrones.mixin.client.SoundEngineAccessor;
import ru.liko.wrbdrones.mixin.client.SoundManagerAccessor;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ShahedOpenALFilters {

    private static boolean initialized = false;
    private static boolean efxAvailable = false;
    private static int lowPassFilter = 0;

    private ShahedOpenALFilters() {
    }

    public static boolean isAvailable() {
        ensureInitialized();
        return efxAvailable;
    }

    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        try {
            long currentContext = ALC10.alcGetCurrentContext();
            if (currentContext == 0L) return;
            long currentDevice = ALC10.alcGetContextsDevice(currentContext);
            if (currentDevice == 0L) return;

            if (!ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
                return;
            }

            lowPassFilter = EXTEfx.alGenFilters();
            if (lowPassFilter == 0) return;

            EXTEfx.alFilteri(lowPassFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAIN, 1.0f);
            EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAINHF, 1.0f);

            efxAvailable = true;
        } catch (Exception e) {
            efxAvailable = false;
        }
    }

    /**
     * @param sourceID OpenAL source ID
     * @param gain     overall gain through filter (0-1)
     * @param gainHF   high-frequency gain (0=fully muffled, 1=no filter)
     */
    public static void applyToSource(int sourceID, float gain, float gainHF) {
        if (!efxAvailable || sourceID == 0) return;

        EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAIN, clamp01(gain));
        EXTEfx.alFilterf(lowPassFilter, EXTEfx.AL_LOWPASS_GAINHF, clamp01(gainHF));
        AL11.alSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER, lowPassFilter);
    }

    public static void removeFromSource(int sourceID) {
        if (!efxAvailable || sourceID == 0) return;
        AL11.alSourcei(sourceID, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
    }

    /**
     * Resolves the OpenAL source ID for a SoundInstance and applies the filter
     * on the sound thread via ChannelHandle.execute().
     */
    public static void applyFilterForInstance(SoundInstance instance, float gain, float gainHF) {
        if (!efxAvailable) return;

        ChannelAccess.ChannelHandle handle = getChannelHandle(instance);
        if (handle == null) return;

        final float g = clamp01(gain);
        final float ghf = clamp01(gainHF);
        handle.execute(channel -> {
            int source = ((ChannelAccessor) channel).getSource();
            applyToSource(source, g, ghf);
        });
    }

    public static void removeFilterForInstance(SoundInstance instance) {
        if (!efxAvailable) return;

        ChannelAccess.ChannelHandle handle = getChannelHandle(instance);
        if (handle == null) return;

        handle.execute(channel -> {
            int source = ((ChannelAccessor) channel).getSource();
            removeFromSource(source);
        });
    }

    private static ChannelAccess.ChannelHandle getChannelHandle(SoundInstance instance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;

        SoundManager sm = mc.getSoundManager();
        SoundEngine engine = ((SoundManagerAccessor) sm).getSoundEngine();
        Map<SoundInstance, ChannelAccess.ChannelHandle> map = ((SoundEngineAccessor) engine).getInstanceToChannel();

        return map.get(instance);
    }

    public static void cleanup() {
        if (efxAvailable && lowPassFilter != 0) {
            EXTEfx.alDeleteFilters(lowPassFilter);
            lowPassFilter = 0;
        }
        efxAvailable = false;
        initialized = false;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
