package ru.liko.wrbdrones.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.registry.ModRecipes;

public record DroneAssemblyRecipe(int assemblyTicks, List<DroneAssemblyIngredient> ingredients, ItemStack result)
        implements Recipe<RecipeInput> {
    private static final Codec<ItemStack> RESULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemStack::getItem),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(ItemStack::getCount))
            .apply(instance, ItemStack::new));

    @Override
    public boolean matches(@NotNull RecipeInput input, @NotNull Level level) {
        return false;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull RecipeInput input, HolderLookup.@NotNull Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return this.result.copy();
    }

    @Override
    public @NotNull NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (DroneAssemblyIngredient ingredient : this.ingredients) {
            list.add(ingredient.ingredient());
        }
        return list;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipes.DRONE_ASSEMBLY_SERIALIZER.get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return ModRecipes.DRONE_ASSEMBLY_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<DroneAssemblyRecipe> {
        private static final StreamCodec<RegistryFriendlyByteBuf, List<DroneAssemblyIngredient>> INGREDIENTS_STREAM_CODEC = DroneAssemblyIngredient.STREAM_CODEC
                .apply(ByteBufCodecs.list());

        private static final MapCodec<DroneAssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(
                        Codec.intRange(1, 24000).fieldOf("assembly_ticks")
                                .forGetter(DroneAssemblyRecipe::assemblyTicks),
                        DroneAssemblyIngredient.CODEC.listOf().fieldOf("ingredients")
                                .forGetter(DroneAssemblyRecipe::ingredients),
                        RESULT_CODEC.fieldOf("result").forGetter(DroneAssemblyRecipe::result))
                .apply(instance, DroneAssemblyRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, DroneAssemblyRecipe> STREAM_CODEC = StreamCodec
                .composite(
                        ByteBufCodecs.VAR_INT,
                        DroneAssemblyRecipe::assemblyTicks,
                        INGREDIENTS_STREAM_CODEC,
                        DroneAssemblyRecipe::ingredients,
                        ItemStack.STREAM_CODEC,
                        DroneAssemblyRecipe::result,
                        DroneAssemblyRecipe::new);

        @Override
        public @NotNull MapCodec<DroneAssemblyRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, DroneAssemblyRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
