package ru.liko.wrbdrones.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.liko.wrbdrones.client.DroneInputHandler;
import ru.liko.wrbdrones.client.DronePostChainHandler;

@OnlyIn(Dist.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Unique
    private static final int FULL_BRIGHT_LIGHT = 15728880;

    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), argsOnly = true, index = 6)
    private int wrbdrones$forceFullBright(int packedLight) {
        if (wrbdrones$isThermalVisionActive()) {
            return FULL_BRIGHT_LIGHT;
        }
        return packedLight;
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
