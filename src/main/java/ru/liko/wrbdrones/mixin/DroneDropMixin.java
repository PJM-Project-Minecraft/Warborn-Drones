package ru.liko.wrbdrones.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Перехватывает {@link DroneEntity#droneDrop(Player)} для предметов из мода
 * {@code warbornexplosives}. SBW не знает о полях этих гранат
 * ({@code Fuse}, {@code IsImpact}, осколочные параметры) и при сбросе через
 * штатный механизм сериализует их через {@code Entity#load(tag)}, что для
 * {@code GrenadeEntity} даёт некорректные значения (несовпадающие имена тегов
 * {@code BlastLethal}/{@code BlastLethalRadius} и т.д.). Здесь мы создаём
 * сущность вручную и вызываем {@code setConfiguration} напрямую.
 */
@Mixin(DroneEntity.class)
public abstract class DroneDropMixin extends net.minecraft.world.entity.Entity {

    private static final Logger WRBDRONES$LOGGER = LogUtils.getLogger();
    private static final String WBE_NAMESPACE = "warbornexplosives";
    private static final String WBE_GRENADE_ENTITY_PATH = "grenade";

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
        if (itemId == null || !WBE_NAMESPACE.equals(itemId.getNamespace())) {
            return;
        }

        Item item = currentItem.getItem();
        String itemClassName = item.getClass().getName();
        // Берём только настоящие GrenadeItem из WBE — у SmokeGrenadeItem другая API.
        if (!itemClassName.contains(WBE_NAMESPACE) || !itemClassName.endsWith(".GrenadeItem")) {
            WRBDRONES$LOGGER.debug(
                    "[WRBDrones/DroneDropMixin] WBE-предмет {} не является GrenadeItem ({}), отдаю на откуп SBW",
                    itemId, itemClassName);
            return;
        }

        DroneEntity drone = (DroneEntity) (Object) this;

        EntityType<?> grenadeEntityType = BuiltInRegistries.ENTITY_TYPE
                .get(ResourceLocation.fromNamespaceAndPath(WBE_NAMESPACE, WBE_GRENADE_ENTITY_PATH));
        if (grenadeEntityType == null) {
            WRBDRONES$LOGGER.warn(
                    "[WRBDrones/DroneDropMixin] не найден EntityType warbornexplosives:grenade — мод WBE не загружен?");
            return;
        }

        Entity grenadeEntity = grenadeEntityType.create(level());
        if (grenadeEntity == null) {
            WRBDRONES$LOGGER.warn("[WRBDrones/DroneDropMixin] EntityType.create вернул null для {}", itemId);
            return;
        }

        // Дальше — даже если что-то частично упадёт, мы всё равно отменим SBW-сброс,
        // потому что иначе SBW дорисует сущность через Entity#load с неполными данными.
        try {
            grenadeEntity.setPos(drone.getX(), drone.getY() - 0.09, drone.getZ());

            if (grenadeEntity instanceof ThrowableItemProjectile throwable) {
                throwable.setItem(currentItem.copyWithCount(1));
            }

            applyGrenadeConfiguration(item, grenadeEntity, itemId);

            if (player != null && grenadeEntity instanceof Projectile projectile) {
                projectile.setOwner(player);
            }

            Vec3 vec3 = new Vec3(
                    0.2 * drone.getDeltaMovement().x,
                    0.2 * drone.getDeltaMovement().y,
                    0.2 * drone.getDeltaMovement().z);
            grenadeEntity.setDeltaMovement(vec3);
            double horizontal = vec3.horizontalDistance();
            grenadeEntity.setYRot((float) (Mth.atan2(vec3.x, vec3.z) * (180F / (float) Math.PI)));
            grenadeEntity.setXRot((float) (Mth.atan2(vec3.y, horizontal) * (180F / (float) Math.PI)));
            grenadeEntity.yRotO = grenadeEntity.getYRot();
            grenadeEntity.xRotO = grenadeEntity.getXRot();

            boolean added = level().addFreshEntity(grenadeEntity);
            WRBDRONES$LOGGER.info(
                    "[WRBDrones/DroneDropMixin] сброшена WBE-граната {} с дрона {} в {} (added={})",
                    itemId, drone.getStringUUID(), grenadeEntity.position(), added);
        } catch (Throwable t) {
            WRBDRONES$LOGGER.error(
                    "[WRBDrones/DroneDropMixin] не удалось полностью настроить WBE-гранату {}, но spawn выполнен с дефолтами",
                    itemId, t);
        }

        ci.cancel();
    }

    private static void applyGrenadeConfiguration(Item item, Entity grenadeEntity, ResourceLocation itemId) {
        try {
            Class<?> itemClass = item.getClass();
            int fuseTime = (int) itemClass.getMethod("getFuseTime").invoke(item);
            boolean isImpact = (boolean) itemClass.getMethod("isImpact").invoke(item);
            int shrapnelCount = (int) itemClass.getMethod("getShrapnelCount").invoke(item);
            float shrapnelDamage = (float) itemClass.getMethod("getShrapnelDamage").invoke(item);
            double shrapnelRange = (double) itemClass.getMethod("getShrapnelRange").invoke(item);
            float blastLethalRadius = (float) itemClass.getMethod("getBlastLethalRadius").invoke(item);
            float blastMaxRadius = (float) itemClass.getMethod("getBlastMaxRadius").invoke(item);
            float shrapnelSpeedCap = (float) itemClass.getMethod("getShrapnelSpeedCap").invoke(item);

            Method setConfigMethod = grenadeEntity.getClass().getMethod("setConfiguration",
                    int.class, boolean.class, int.class, float.class, double.class, float.class, float.class,
                    float.class);
            setConfigMethod.invoke(grenadeEntity, fuseTime, isImpact, shrapnelCount, shrapnelDamage,
                    shrapnelRange, blastLethalRadius, blastMaxRadius, shrapnelSpeedCap);
        } catch (NoSuchMethodException e) {
            WRBDRONES$LOGGER.warn(
                    "[WRBDrones/DroneDropMixin] WBE API-метод не найден для {} ({}), граната будет с дефолтными параметрами",
                    itemId, e.getMessage());
        } catch (Exception e) {
            WRBDRONES$LOGGER.warn(
                    "[WRBDrones/DroneDropMixin] не удалось применить конфигурацию WBE-гранаты {}: {}",
                    itemId, e.toString());
        }
    }
}
