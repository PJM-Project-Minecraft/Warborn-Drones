package ru.liko.wrbdrones.mixin;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

/**
 * Отключает партиклы повреждений (дым, огонь) для AddonDroneEntity
 */
@Mixin(VehicleEntity.class)
public class VehicleDamageParticleMixin {

    @Inject(method = "lowHealthWarning", at = @At("HEAD"), cancellable = true)
    private void wrbdrones$cancelDamageParticles(CallbackInfo ci) {
        if ((Object) this instanceof AddonDroneEntity) {
            ci.cancel();
        }
    }
}
