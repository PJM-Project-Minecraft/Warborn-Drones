package ru.liko.wrbdrones.client.model;

import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.LancetLaunchPlatformEntity;
import software.bernie.geckolib.model.GeoModel;

public class LancetLaunchPlatformModel extends GeoModel<LancetLaunchPlatformEntity> {
    private static final ResourceLocation MODEL = Wrbdrones.id("geo/puskovaya.geo.json");
    private static final ResourceLocation TEXTURE = Wrbdrones.id("textures/entity/puskovaya.png");
    private static final ResourceLocation ANIMATION = Wrbdrones.id("animations/drone.animation.json");

    @Override
    public ResourceLocation getModelResource(LancetLaunchPlatformEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LancetLaunchPlatformEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LancetLaunchPlatformEntity animatable) {
        return ANIMATION;
    }
}
