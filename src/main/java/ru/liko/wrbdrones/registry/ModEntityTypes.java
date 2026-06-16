package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.FpvDroneEntity;
import ru.liko.wrbdrones.entity.LancetLaunchPlatformEntity;
import ru.liko.wrbdrones.entity.MavicDroneNoDropEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;
import ru.liko.wrbdrones.entity.RebEntity;
import ru.liko.wrbdrones.entity.RebMiniEntity;
import ru.liko.wrbdrones.entity.Shahed136Entity;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;

public final class ModEntityTypes {
        private ModEntityTypes() {
        }

        public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister
                        .create(BuiltInRegistries.ENTITY_TYPE, Wrbdrones.MODID);

        public static final net.minecraft.resources.ResourceLocation DEFAULT_ANIMATION = Wrbdrones
                        .loc("animations/drone.animation.json");

        public static final DeferredHolder<EntityType<?>, EntityType<MavicDroneWithDropEntity>> MAVIC_DRONE_WITH_DROP = REGISTRY
                        .register("mavic_drone_with_drop",
                                        () -> EntityType.Builder
                                                        .<MavicDroneWithDropEntity>of(MavicDroneWithDropEntity::new,
                                                                        MobCategory.MISC)
                                                        .sized(0.8F, 0.35F)
                                                        .clientTrackingRange(32)
                                                        .updateInterval(1)
                                                        .build(Wrbdrones.loc("mavic_drone_with_drop").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<MavicDroneNoDropEntity>> MAVIC_DRONE_NO_DROP = REGISTRY
                        .register("mavic_drone_no_drop",
                                        () -> EntityType.Builder
                                                        .<MavicDroneNoDropEntity>of(MavicDroneNoDropEntity::new,
                                                                        MobCategory.MISC)
                                                        .sized(0.8F, 0.35F)
                                                        .clientTrackingRange(32)
                                                        .updateInterval(1)
                                                        .build(Wrbdrones.loc("mavic_drone_no_drop").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<FpvDroneEntity>> FPV_DRONE = REGISTRY.register(
                        "fpv_drone",
                        () -> EntityType.Builder.<FpvDroneEntity>of(FpvDroneEntity::new, MobCategory.MISC)
                                        .sized(0.65F, 0.32F)
                                        .clientTrackingRange(32)
                                        .updateInterval(1)
                                        .build(Wrbdrones.loc("fpv_drone").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<ZalaLancetEntity>> ZALA_LANCET = REGISTRY
                        .register("zala_lancet",
                                        () -> EntityType.Builder
                                                        .<ZalaLancetEntity>of(ZalaLancetEntity::new,
                                                                        MobCategory.MISC)
                                                        .sized(1.25F, 0.45F)
                                                        .clientTrackingRange(96)
                                                        .updateInterval(1)
                                                        .build(Wrbdrones.loc("zala_lancet").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<LancetLaunchPlatformEntity>> LANCET_LAUNCH_PLATFORM = REGISTRY
                        .register("lancet_launch_platform",
                                        () -> EntityType.Builder
                                                        .<LancetLaunchPlatformEntity>of(
                                                                        LancetLaunchPlatformEntity::new,
                                                                        MobCategory.MISC)
                                                        .sized(4.0F, 2.4F)
                                                        .clientTrackingRange(96)
                                                        .updateInterval(2)
                                                        .build(Wrbdrones.loc("lancet_launch_platform").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<RebEntity>> REB = REGISTRY.register("reb",
                        () -> EntityType.Builder.<RebEntity>of(RebEntity::new, MobCategory.MISC)
                                        .sized(1.0F, 2.0F)
                                        .clientTrackingRange(8)
                                        .updateInterval(1)
                                        .build(Wrbdrones.loc("reb").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<RebMiniEntity>> REB_MINI = REGISTRY.register(
                        "reb_mini",
                        () -> EntityType.Builder.<RebMiniEntity>of(RebMiniEntity::new, MobCategory.MISC)
                                        .sized(0.6F, 1.0F)
                                        .clientTrackingRange(8)
                                        .updateInterval(1)
                                        .build(Wrbdrones.loc("reb_mini").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<Shahed136Entity>> SHAHED136 = REGISTRY.register(
                        "shahed136",
                        () -> EntityType.Builder.<Shahed136Entity>of(Shahed136Entity::new, MobCategory.MISC)
                                        .sized(3.0F, 0.8F)
                                        .clientTrackingRange(128)
                                        .updateInterval(1)
                                        .build(Wrbdrones.loc("shahed136").toString()));

        public static final DeferredHolder<EntityType<?>, EntityType<PlayerDecoyEntity>> PLAYER_DECOY = REGISTRY
                        .register("player_decoy",
                                        () -> EntityType.Builder
                                                        .<PlayerDecoyEntity>of(PlayerDecoyEntity::new, MobCategory.MISC)
                                                        .sized(0.6F, 1.8F)
                                                        .clientTrackingRange(16)
                                                        .updateInterval(2)
                                                        .build(Wrbdrones.loc("player_decoy").toString()));
}
