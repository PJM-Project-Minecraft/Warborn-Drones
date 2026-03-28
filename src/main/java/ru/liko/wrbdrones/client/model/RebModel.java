package ru.liko.wrbdrones.client.model;

import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.RebEntity;
import software.bernie.geckolib.model.GeoModel;

public class RebModel extends GeoModel<RebEntity> {
    private static final ResourceLocation MODEL = Wrbdrones.id("geo/reb_max.geo.json");
    private static final ResourceLocation TEXTURE = Wrbdrones.id("textures/entity/reb_max.png");
    private static final ResourceLocation ANIMATION = Wrbdrones.id("animations/on_off.animation.json");

    @Override
    public ResourceLocation getModelResource(RebEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RebEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RebEntity animatable) {
        return ANIMATION;
    }
}
