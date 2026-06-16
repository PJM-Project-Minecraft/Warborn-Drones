package ru.liko.wrbdrones.client.model;

import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.item.DronetableBlockItem;
import software.bernie.geckolib.model.GeoModel;

public class DronetableItemModel extends GeoModel<DronetableBlockItem> {
    private static final ResourceLocation MODEL = Wrbdrones.id("geo/drontable.geo.json");
    private static final ResourceLocation TEXTURE = Wrbdrones.id("textures/entity/dronetable.png");

    @Override
    public ResourceLocation getModelResource(DronetableBlockItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(DronetableBlockItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(DronetableBlockItem animatable) {
        return null;
    }
}
