package ru.liko.wrbdrones.client.renderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import ru.liko.wrbdrones.client.model.DronetableItemModel;
import ru.liko.wrbdrones.item.DronetableBlockItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class DronetableBlockItemRenderer extends GeoItemRenderer<DronetableBlockItem> {
    public DronetableBlockItemRenderer() {
        super(new DronetableItemModel());
    }

    @Override
    public RenderType getRenderType(DronetableBlockItem animatable, ResourceLocation texture,
            MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(this.getTextureLocation(animatable));
    }
}
