package ru.liko.wrbdrones.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.wrbdrones.client.DroneInputHandler;
import ru.liko.wrbdrones.client.DronePostChainHandler;

@OnlyIn(Dist.CLIENT)
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    @Unique
    private static final int FULL_BRIGHT_LIGHT = 15728880;

    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void wrbdrones$forceFullBright(T entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (wrbdrones$isThermalVisionActive()) {
            if (entity instanceof com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity) {
                cir.setReturnValue(FULL_BRIGHT_LIGHT);
            }
        }
    }

    @Unique
    private boolean wrbdrones$isThermalVisionActive() {
        if (!DronePostChainHandler.isThermalEnabled) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }
        return DroneInputHandler.getControlledLancet(mc.player) != null;
    }
}
