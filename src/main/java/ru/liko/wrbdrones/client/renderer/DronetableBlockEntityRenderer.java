package ru.liko.wrbdrones.client.renderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.block.DronetableBlock;
import ru.liko.wrbdrones.block.entity.DronetableBlockEntity;
import ru.liko.wrbdrones.client.model.DronetableBlockModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class DronetableBlockEntityRenderer extends GeoBlockRenderer<DronetableBlockEntity> {
    public DronetableBlockEntityRenderer() {
        super(new DronetableBlockModel());
    }

    @Override
    public RenderType getRenderType(DronetableBlockEntity animatable, ResourceLocation texture,
            MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(this.getTextureLocation(animatable));
    }

    @Override
    protected @NotNull Direction getFacing(DronetableBlockEntity animatable) {
        return animatable.getBlockState().getValue(DronetableBlock.FACING);
    }
}
