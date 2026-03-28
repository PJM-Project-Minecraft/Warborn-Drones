package ru.liko.wrbdrones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ru.liko.wrbdrones.client.model.RebModel;
import ru.liko.wrbdrones.entity.RebEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class RebRenderer extends GeoEntityRenderer<RebEntity> {
    public RebRenderer(EntityRendererProvider.Context context) {
        super(context, new RebModel());
        this.shadowRadius = 0.5f;
    }

    @Override
    protected void applyRotations(RebEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw,
            float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        // Применяем поворот сущности
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
    }
}
