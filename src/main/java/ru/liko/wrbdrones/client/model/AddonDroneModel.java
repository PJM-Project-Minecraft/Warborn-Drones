package ru.liko.wrbdrones.client.model;

import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import software.bernie.geckolib.model.GeoModel;

public class AddonDroneModel<T extends AddonDroneEntity> extends GeoModel<T> {
    @Override
    public ResourceLocation getModelResource(T animatable) {
        return animatable.getModelResource();
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return animatable.getTextureResource();
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return animatable.getAnimationResource();
    }
}
