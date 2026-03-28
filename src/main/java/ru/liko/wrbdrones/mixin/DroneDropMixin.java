package ru.liko.wrbdrones.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

@Mixin(DroneEntity.class)
public abstract class DroneDropMixin extends net.minecraft.world.entity.Entity {

    // Dummy constructor for mixin extending Entity
    protected DroneDropMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    public ItemStack currentItem;

    @Inject(method = "droneDrop", at = @At("HEAD"), cancellable = true)
    private void wrbdrones$handleWarbornExplosivesDrop(@Nullable Player player, CallbackInfo ci) {
        if (currentItem == null || currentItem.isEmpty()) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(currentItem.getItem());
        if (itemId == null || !itemId.getNamespace().equals("warbornexplosives")) {
            return;
        }

        // Check if the item is a WRB Explosives grenade
        Item item = currentItem.getItem();
        String itemClassName = item.getClass().getName();
        if (!itemClassName.contains("warbornexplosives") || !itemClassName.contains("GrenadeItem")) {
            return;
        }

        try {
            DroneEntity drone = (DroneEntity) (Object) this;
            
            // Get entity type from registry
            EntityType<?> grenadeEntityType = BuiltInRegistries.ENTITY_TYPE
                .get(ResourceLocation.fromNamespaceAndPath("warbornexplosives", "grenade"));
            
            if (grenadeEntityType == null) {
                return;
            }
            
            // Create the grenade entity
            Entity grenadeEntity = grenadeEntityType.create(level());
            if (grenadeEntity == null) {
                return;
            }
            
            // Set position
            grenadeEntity.setPos(drone.getX(), drone.getY() - 0.09, drone.getZ());
            
            // Set the item using reflection (ThrowableItemProjectile.setItem)
            if (grenadeEntity instanceof ThrowableItemProjectile throwable) {
                throwable.setItem(currentItem.copyWithCount(1));
            }
            
            // Get grenade configuration from item using reflection
            Class<?> itemClass = item.getClass();
            int fuseTime = (int) itemClass.getMethod("getFuseTime").invoke(item);
            boolean isImpact = (boolean) itemClass.getMethod("isImpact").invoke(item);
            int shrapnelCount = (int) itemClass.getMethod("getShrapnelCount").invoke(item);
            float shrapnelDamage = (float) itemClass.getMethod("getShrapnelDamage").invoke(item);
            double shrapnelRange = (double) itemClass.getMethod("getShrapnelRange").invoke(item);
            float blastLethalRadius = (float) itemClass.getMethod("getBlastLethalRadius").invoke(item);
            float blastMaxRadius = (float) itemClass.getMethod("getBlastMaxRadius").invoke(item);
            float shrapnelSpeedCap = (float) itemClass.getMethod("getShrapnelSpeedCap").invoke(item);
            
            // Call setConfiguration on the grenade entity
            Method setConfigMethod = grenadeEntity.getClass().getMethod("setConfiguration",
                int.class, boolean.class, int.class, float.class, double.class, float.class, float.class, float.class);
            setConfigMethod.invoke(grenadeEntity, fuseTime, isImpact, shrapnelCount, shrapnelDamage,
                shrapnelRange, blastLethalRadius, blastMaxRadius, shrapnelSpeedCap);
            
            // Set owner
            if (player != null && grenadeEntity instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                projectile.setOwner(player);
            }
            
            // Set movement based on drone velocity
            Vec3 vec3 = new Vec3(
                0.2 * drone.getDeltaMovement().x,
                0.2 * drone.getDeltaMovement().y,
                0.2 * drone.getDeltaMovement().z
            );
            grenadeEntity.setDeltaMovement(vec3);
            double d0 = vec3.horizontalDistance();
            grenadeEntity.setYRot((float) (Mth.atan2(vec3.x, vec3.z) * (180F / (float) Math.PI)));
            grenadeEntity.setXRot((float) (Mth.atan2(vec3.y, d0) * (180F / (float) Math.PI)));
            grenadeEntity.yRotO = grenadeEntity.getYRot();
            grenadeEntity.xRotO = grenadeEntity.getXRot();
            
            level().addFreshEntity(grenadeEntity);
            
            // Cancel the original method
            ci.cancel();
        } catch (Exception e) {
            // If reflection fails, let the original method handle it
        }
    }
}
