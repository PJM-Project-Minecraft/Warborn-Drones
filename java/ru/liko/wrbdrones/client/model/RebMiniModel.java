package ru.liko.wrbdrones.client.model;

import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.RebMiniEntity;
import software.bernie.geckolib.model.GeoModel;

public class RebMiniModel extends GeoModel<RebMiniEntity> {
    private static final ResourceLocation MODEL = Wrbdrones.id("geo/reb_mini.geo.json");
    private static final ResourceLocation TEXTURE = Wrbdrones.id("textures/entity/reb_mini.png");
    private static final ResourceLocation ANIMATION = Wrbdrones.id("animations/on_off.animation.json");

    @Override
    public ResourceLocation getModelResource(RebMiniEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RebMiniEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RebMiniEntity animatable) {
        return ANIMATION;
    }
}
