package ru.liko.wrbdrones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ru.liko.wrbdrones.client.model.LancetLaunchPlatformModel;
import ru.liko.wrbdrones.entity.LancetLaunchPlatformEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LancetLaunchPlatformRenderer extends GeoEntityRenderer<LancetLaunchPlatformEntity> {
    public LancetLaunchPlatformRenderer(EntityRendererProvider.Context context) {
        super(context, new LancetLaunchPlatformModel());
        this.shadowRadius = 0.4f;
    }

    @Override
    protected void applyRotations(LancetLaunchPlatformEntity entity, PoseStack poseStack, float ageInTicks,
            float rotationYaw, float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
    }
}
