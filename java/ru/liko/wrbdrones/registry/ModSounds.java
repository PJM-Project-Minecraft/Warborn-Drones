package ru.liko.wrbdrones.registry;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.liko.wrbdrones.Wrbdrones;

public final class ModSounds {
    private ModSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Wrbdrones.MODID);

    // ===== FPV Дрон =====
    public static final RegistryObject<SoundEvent> FPV_DRONE_ENGINE = SOUNDS.register("fpv_drone_engine",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("fpv_drone_engine"), 48.0f));

    public static final RegistryObject<SoundEvent> FPV_DRONE_ENGINE_INT = SOUNDS.register("fpv_drone_engine_int",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("fpv_drone_engine_int"), 8.0f));

    // ===== РЭБ =====
    public static final RegistryObject<SoundEvent> REB_PLACEMENT_01 = SOUNDS.register("reb_placement_01",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("reb_placement_01"), 16.0f));

    public static final RegistryObject<SoundEvent> REB_PLACEMENT_02 = SOUNDS.register("reb_placement_02",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("reb_placement_02"), 16.0f));

    public static final RegistryObject<SoundEvent> REB_TOGGLE_ON = SOUNDS.register("reb_toggle_on",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("reb_toggle_on"), 16.0f));

    public static final RegistryObject<SoundEvent> REB_TOGGLE_OFF = SOUNDS.register("reb_toggle_off",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("reb_toggle_off"), 16.0f));

    // ===== Shahed-136 (реалистичная звуковая система) =====
    
    // Звук запуска двигателя (раскрутка пропеллера)
    public static final RegistryObject<SoundEvent> SHAHED136_START = SOUNDS.register("shahed136_start",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("shahed136_start"), 96.0f));

    // Основной звук двигателя (мопедный гул, слышен далеко)
    public static final RegistryObject<SoundEvent> SHAHED136_ENGINE = SOUNDS.register("shahed136_engine",
            () -> SoundEvent.createVariableRangeEvent(Wrbdrones.id("shahed136_engine")));

    // Звук пикирования (высокий вой при атаке)
    public static final RegistryObject<SoundEvent> SHAHED136_DIVE = SOUNDS.register("shahed136_dive",
            () -> SoundEvent.createVariableRangeEvent(Wrbdrones.id("shahed136_dive")));

    // Звук остановки двигателя (перед взрывом)
    public static final RegistryObject<SoundEvent> SHAHED136_ENGINE_END = SOUNDS.register("shahed136_engine_end",
            () -> SoundEvent.createFixedRangeEvent(Wrbdrones.id("shahed136_engine_end"), 96.0f));
    
    // Звук пролёта мимо (Doppler flyby)
    public static final RegistryObject<SoundEvent> SHAHED136_FLYBY = SOUNDS.register("shahed136_flyby",
            () -> SoundEvent.createVariableRangeEvent(Wrbdrones.id("shahed136_flyby")));

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}

