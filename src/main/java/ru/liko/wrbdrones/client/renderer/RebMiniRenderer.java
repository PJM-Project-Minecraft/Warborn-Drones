package ru.liko.wrbdrones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ru.liko.wrbdrones.client.model.RebMiniModel;
import ru.liko.wrbdrones.entity.RebMiniEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RebMiniRenderer extends GeoEntityRenderer<RebMiniEntity> {
    public RebMiniRenderer(EntityRendererProvider.Context context) {
        super(context, new RebMiniModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    protected void applyRotations(RebMiniEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        // Применяем поворот сущности
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
    }
}
