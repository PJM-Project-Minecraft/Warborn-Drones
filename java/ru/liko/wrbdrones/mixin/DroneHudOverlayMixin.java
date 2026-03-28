package ru.liko.wrbdrones.mixin;

import com.atsuishio.superbwarfare.client.overlay.DroneHudOverlay;
import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

@Mixin(DroneHudOverlay.class)
public class DroneHudOverlayMixin {

    /**
     * Отключает SBW overlay для наших дронов (AddonDroneEntity)
     * Добавляет проверку типа дрона в начале метода render
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void wrbdrones$checkAddonDrone(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, 
                                           int screenWidth, int screenHeight, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        
        // Проверяем, используется ли monitor и связан ли он с дроном
        if (stack.is(ModItems.MONITOR.get()) && stack.getOrCreateTag().getBoolean("Using") 
                && stack.getOrCreateTag().getBoolean("Linked")) {
            
            DroneEntity drone = EntityFindUtil.findDrone(player.level(), 
                    stack.getOrCreateTag().getString("LinkedDrone"));
            
            // Если дрон является нашим (AddonDroneEntity), отменяем рендеринг SBW overlay
            if (drone instanceof AddonDroneEntity) {
                ci.cancel();
            }
        }
    }
}

