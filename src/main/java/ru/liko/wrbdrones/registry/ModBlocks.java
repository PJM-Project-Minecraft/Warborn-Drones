package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.block.DronetableBlock;
import ru.liko.wrbdrones.block.DronetableHitboxBlock;

public final class ModBlocks {
    private ModBlocks() {
    }

    public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(BuiltInRegistries.BLOCK,
            Wrbdrones.MODID);

    public static final DeferredHolder<Block, DronetableBlock> DRONETABLE = REGISTRY.register("dronetable",
            () -> new DronetableBlock());

    public static final DeferredHolder<Block, DronetableHitboxBlock> DRONETABLE_HITBOX = REGISTRY.register(
            "dronetable_hitbox", () -> new DronetableHitboxBlock());
}
