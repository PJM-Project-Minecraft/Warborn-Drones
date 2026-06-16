package ru.liko.wrbdrones.client.model;

import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.block.entity.DronetableBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public class DronetableBlockModel extends GeoModel<DronetableBlockEntity> {
    private static final ResourceLocation MODEL = Wrbdrones.id("geo/drontable.geo.json");
    private static final ResourceLocation TEXTURE = Wrbdrones.id("textures/entity/dronetable.png");

    @Override
    public ResourceLocation getModelResource(DronetableBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DronetableBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DronetableBlockEntity animatable) {
        return null;
    }
}
