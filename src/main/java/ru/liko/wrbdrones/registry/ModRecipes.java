package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.recipe.DroneAssemblyRecipe;

public final class ModRecipes {
    private ModRecipes() {
    }

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister
            .create(BuiltInRegistries.RECIPE_SERIALIZER, Wrbdrones.MODID);
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE,
            Wrbdrones.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DroneAssemblyRecipe>> DRONE_ASSEMBLY_SERIALIZER = SERIALIZERS
            .register("drone_assembling", DroneAssemblyRecipe.Serializer::new);

    public static final DeferredHolder<RecipeType<?>, RecipeType<DroneAssemblyRecipe>> DRONE_ASSEMBLY_TYPE = TYPES
            .register("drone_assembling", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return Wrbdrones.MODID + ":drone_assembling";
                }
            });
}
