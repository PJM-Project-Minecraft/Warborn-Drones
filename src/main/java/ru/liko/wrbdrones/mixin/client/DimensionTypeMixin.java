package ru.liko.wrbdrones.mixin.client;

import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.wrbdrones.client.DronePostChainHandler;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {

    @Inject(method = "timeOfDay", at = @At("HEAD"), cancellable = true)
    private void wrbdrones$forceNightForThermal(long dayTime, CallbackInfoReturnable<Float> cir) {
        if (DronePostChainHandler.isThermalEnabled) {
            // Force midnight (0.5 in getTimeOfDay scale)
            cir.setReturnValue(0.5F);
        }
    }
}
