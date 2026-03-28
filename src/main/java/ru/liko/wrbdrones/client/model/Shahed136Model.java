package ru.liko.wrbdrones.client.model;

import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.Shahed136Entity;
import software.bernie.geckolib.model.GeoModel;

public class Shahed136Model extends GeoModel<Shahed136Entity> {

    private static final ResourceLocation MODEL = Wrbdrones.id("geo/shahed136.geo.json");
    private static final ResourceLocation TEXTURE = Wrbdrones.id("textures/entity/shahed136.png");
    private static final ResourceLocation ANIMATION = Wrbdrones.id("animations/shahed136.animation.json");

    @Override
    public ResourceLocation getModelResource(Shahed136Entity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(Shahed136Entity entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(Shahed136Entity entity) {
        return ANIMATION;
    }
}
