package ru.liko.wrbdrones.mixin.client;

import com.atsuishio.superbwarfare.client.renderer.special.OBBRenderer;
import com.atsuishio.superbwarfare.entity.OBBEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Отладочная отрисовка OBB (F3+B) для наших сущностей, реализующих {@link OBBEntity}
 * вручную, минуя SBW {@link VehicleEntity} — например {@code Shahed136Entity}.
 * Мишин SBW {@code EntityRenderDispatcherMixin} рисует рамки только для
 * {@code VehicleEntity}, поэтому без этой добивки Шахед оставался бы с одним
 * ванильным AABB на экране, хотя попадания уже считаются по OBB.
 */
@Mixin(EntityRenderDispatcher.class)
public class ObbHitboxRenderMixin {

    @Inject(method = "renderHitbox", at = @At("RETURN"))
    private static void wrbdrones$renderObbHitbox(PoseStack poseStack, VertexConsumer buffer, Entity entity,
                                                  float red, float green, float blue, float alpha, CallbackInfo ci) {
        // VehicleEntity рисует SBW — не дублируем.
        if (!(entity instanceof OBBEntity obbEntity) || entity instanceof VehicleEntity || obbEntity.enableAABB()) {
            return;
        }
        Vec3 position = entity.position();
        for (OBB obb : obbEntity.getOBBs()) {
            OBBRenderer.INSTANCE.renderOBB(poseStack, buffer,
                    obb.center.x() - position.x(),
                    obb.center.y() - position.y(),
                    obb.center.z() - position.z(),
                    obb.rotation(),
                    obb.extents().x(), obb.extents().y(), obb.extents().z(),
                    0.0F, 1.0F, 0.0F, 1.0F);
        }
    }
}
