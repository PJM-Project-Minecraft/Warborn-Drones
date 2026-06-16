package ru.liko.wrbdrones.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

public record DroneAssemblyIngredient(Ingredient ingredient, int count) {
    public static final Codec<DroneAssemblyIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(DroneAssemblyIngredient::ingredient),
            Codec.intRange(1, 9999).optionalFieldOf("count", 1).forGetter(DroneAssemblyIngredient::count))
            .apply(instance, DroneAssemblyIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DroneAssemblyIngredient> STREAM_CODEC = StreamCodec
            .composite(
                    Ingredient.CONTENTS_STREAM_CODEC,
                    DroneAssemblyIngredient::ingredient,
                    ByteBufCodecs.VAR_INT,
                    DroneAssemblyIngredient::count,
                    DroneAssemblyIngredient::new);
}
