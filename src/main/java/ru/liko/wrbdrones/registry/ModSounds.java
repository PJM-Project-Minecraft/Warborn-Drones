package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;

public final class ModSounds {
        private ModSounds() {
        }

        public static final DeferredRegister<SoundEvent> REGISTRY = DeferredRegister
                        .create(BuiltInRegistries.SOUND_EVENT, Wrbdrones.MODID);

        // ===== FPV Дрон (реалистичная звуковая система, как у Shahed) =====
        public static final DeferredHolder<SoundEvent, SoundEvent> FPV_DRONE_ENGINE = REGISTRY.register(
                        "fpv_drone_engine",
                        () -> SoundEvent.createVariableRangeEvent(Wrbdrones.loc("fpv_drone_engine")));

        public static final DeferredHolder<SoundEvent, SoundEvent> FPV_DRONE_ENGINE_INT = REGISTRY.register(
                        "fpv_drone_engine_int",
                        () -> SoundEvent.createFixedRangeEvent(Wrbdrones.loc("fpv_drone_engine_int"), 8.0f));

        // ===== РЭБ =====
        public static final DeferredHolder<SoundEvent, SoundEvent> REB_PLACEMENT_01 = REGISTRY.register(
                        "reb_placement_01",
                        () -> SoundEvent.createFixedRangeEvent(Wrbdrones.loc("reb_placement_01"), 16.0f));

        public static final DeferredHolder<SoundEvent, SoundEvent> REB_PLACEMENT_02 = REGISTRY.register(
                        "reb_placement_02",
                        () -> SoundEvent.createFixedRangeEvent(Wrbdrones.loc("reb_placement_02"), 16.0f));

        public static final DeferredHolder<SoundEvent, SoundEvent> REB_TOGGLE_ON = REGISTRY.register("reb_toggle_on",
                        () -> SoundEvent.createFixedRangeEvent(Wrbdrones.loc("reb_toggle_on"), 16.0f));

        public static final DeferredHolder<SoundEvent, SoundEvent> REB_TOGGLE_OFF = REGISTRY.register("reb_toggle_off",
                        () -> SoundEvent.createFixedRangeEvent(Wrbdrones.loc("reb_toggle_off"), 16.0f));

        // ===== Shahed-136 (реалистичная звуковая система) =====

        // Звук запуска двигателя (раскрутка пропеллера)
        public static final DeferredHolder<SoundEvent, SoundEvent> SHAHED136_START = REGISTRY.register(
                        "shahed136_start",
                        () -> SoundEvent.createFixedRangeEvent(Wrbdrones.loc("shahed136_start"), 96.0f));

        // Основной звук двигателя (мопедный гул, слышен далеко)
        public static final DeferredHolder<SoundEvent, SoundEvent> SHAHED136_ENGINE = REGISTRY.register(
                        "shahed136_engine",
                        () -> SoundEvent.createVariableRangeEvent(Wrbdrones.loc("shahed136_engine")));

        // Звук пикирования (высокий вой при атаке)
        public static final DeferredHolder<SoundEvent, SoundEvent> SHAHED136_DIVE = REGISTRY.register("shahed136_dive",
                        () -> SoundEvent.createVariableRangeEvent(Wrbdrones.loc("shahed136_dive")));

        // Звук остановки двигателя (перед взрывом)
        public static final DeferredHolder<SoundEvent, SoundEvent> SHAHED136_ENGINE_END = REGISTRY.register(
                        "shahed136_engine_end",
                        () -> SoundEvent.createFixedRangeEvent(Wrbdrones.loc("shahed136_engine_end"), 96.0f));

        // Звук пролёта мимо (Doppler flyby)
        public static final DeferredHolder<SoundEvent, SoundEvent> SHAHED136_FLYBY = REGISTRY.register(
                        "shahed136_flyby",
                        () -> SoundEvent.createVariableRangeEvent(Wrbdrones.loc("shahed136_flyby")));

}

