package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.block.entity.DronetableBlockEntity;

public final class ModBlockEntities {
    private ModBlockEntities() {
    }

    public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister
            .create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Wrbdrones.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DronetableBlockEntity>> DRONETABLE = REGISTRY
            .register("dronetable",
                    () -> BlockEntityType.Builder.of(DronetableBlockEntity::new, ModBlocks.DRONETABLE.get())
                            .build(null));
}
