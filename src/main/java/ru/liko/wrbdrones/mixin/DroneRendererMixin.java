package ru.liko.wrbdrones.mixin;

import com.atsuishio.superbwarfare.client.renderer.entity.DroneRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;

import java.lang.reflect.Field;

@Mixin(DroneRenderer.class)
public class DroneRendererMixin {

    @Shadow
    private Entity entityCache;

    @Inject(method = "renderAttachments", at = @At(value = "INVOKE", 
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void wrbdrones$fixWarbornExplosivesDisplay(DroneEntity entity, float entityYaw, float partialTicks, 
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        try {
            // Check if the cached entity is a WRB Explosives grenade
            if (entityCache == null) {
                return;
            }
            
            // Check if it's a warbornexplosives entity
            String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityCache.getType()).toString();
            if (!entityTypeId.startsWith("warbornexplosives:")) {
                return;
            }
            
            // Get current item from drone
            Field currentItemField = DroneEntity.class.getDeclaredField("currentItem");
            currentItemField.setAccessible(true);
            ItemStack currentItem = (ItemStack) currentItemField.get(entity);
            
            if (currentItem == null || currentItem.isEmpty()) {
                return;
            }
            
            // Set the item on the grenade entity so it renders with the correct model
            if (entityCache instanceof ThrowableItemProjectile throwable) {
                if (throwable.getItem().isEmpty()) {
                    throwable.setItem(currentItem.copyWithCount(1));
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }
}
