package ru.liko.wrbdrones.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.FpvDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneNoDropEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;
import ru.liko.wrbdrones.entity.RebEntity;
import ru.liko.wrbdrones.entity.RebMiniEntity;
import ru.liko.wrbdrones.entity.Shahed136Entity;

public final class ModEntityTypes {
    private ModEntityTypes() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Wrbdrones.MODID);

    public static final ResourceLocation DEFAULT_ANIMATION = Wrbdrones.id("animations/drone.animation.json");

    public static final RegistryObject<EntityType<MavicDroneWithDropEntity>> MAVIC_DRONE_WITH_DROP = ENTITY_TYPES.register("mavic_drone_with_drop",
            () -> EntityType.Builder.<MavicDroneWithDropEntity>of(MavicDroneWithDropEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.35F)
                    .setTrackingRange(512)
                    .setShouldReceiveVelocityUpdates(true)
                    .setUpdateInterval(1)
                    .build(Wrbdrones.id("mavic_drone_with_drop").toString()));

    public static final RegistryObject<EntityType<MavicDroneNoDropEntity>> MAVIC_DRONE_NO_DROP = ENTITY_TYPES.register("mavic_drone_no_drop",
            () -> EntityType.Builder.<MavicDroneNoDropEntity>of(MavicDroneNoDropEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.35F)
                    .setTrackingRange(512)
                    .setShouldReceiveVelocityUpdates(true)
                    .setUpdateInterval(1)
                    .build(Wrbdrones.id("mavic_drone_no_drop").toString()));

    public static final RegistryObject<EntityType<FpvDroneEntity>> FPV_DRONE = ENTITY_TYPES.register("fpv_drone",
            () -> EntityType.Builder.<FpvDroneEntity>of(FpvDroneEntity::new, MobCategory.MISC)
                    .sized(0.65F, 0.32F)
                    .setTrackingRange(512)
                    .setShouldReceiveVelocityUpdates(true)
                    .setUpdateInterval(1)
                    .build(Wrbdrones.id("fpv_drone").toString()));

    public static final RegistryObject<EntityType<RebEntity>> REB = ENTITY_TYPES.register("reb",
            () -> EntityType.Builder.<RebEntity>of(RebEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F)
                    .setTrackingRange(64)
                    .setShouldReceiveVelocityUpdates(true)
                    .setUpdateInterval(1)
                    .build(Wrbdrones.id("reb").toString()));

    public static final RegistryObject<EntityType<RebMiniEntity>> REB_MINI = ENTITY_TYPES.register("reb_mini",
            () -> EntityType.Builder.<RebMiniEntity>of(RebMiniEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.0F)
                    .setTrackingRange(64)
                    .setShouldReceiveVelocityUpdates(true)
                    .setUpdateInterval(1)
                    .build(Wrbdrones.id("reb_mini").toString()));

    public static final RegistryObject<EntityType<Shahed136Entity>> SHAHED136 = ENTITY_TYPES.register("shahed136",
            () -> EntityType.Builder.<Shahed136Entity>of(Shahed136Entity::new, MobCategory.MISC)
                    .sized(3.0F, 0.8F)
                    .setTrackingRange(512)
                    .setShouldReceiveVelocityUpdates(true)
                    .setUpdateInterval(1)
                    .build(Wrbdrones.id("shahed136").toString()));

    public static final RegistryObject<EntityType<PlayerDecoyEntity>> PLAYER_DECOY = ENTITY_TYPES.register("player_decoy",
            () -> EntityType.Builder.<PlayerDecoyEntity>of(PlayerDecoyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .setTrackingRange(128)
                    .setShouldReceiveVelocityUpdates(true)
                    .setUpdateInterval(2)
                    .build(Wrbdrones.id("player_decoy").toString()));

    public static void register(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
