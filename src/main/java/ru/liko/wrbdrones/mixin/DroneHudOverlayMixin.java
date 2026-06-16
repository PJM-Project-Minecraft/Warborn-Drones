package ru.liko.wrbdrones.mixin;

import com.atsuishio.superbwarfare.client.overlay.DroneHudOverlay;
import com.atsuishio.superbwarfare.client.overlay.RenderContext;
import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

/**
 * Отключает рендер HUD дрона из SBW для наших дронов (AddonDroneEntity).
 * <p>
 * В SBW 0.8.9 оверлеи переписали на Kotlin: {@code DroneHudOverlay} стал
 * объектом-наследником {@code CommonOverlay} с методом
 * {@code render(RenderContext)} (Kotlin-extension-функция). Поэтому миксин
 * теперь инжектится в новую сигнатуру.
 */
@Mixin(DroneHudOverlay.class)
public class DroneHudOverlayMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void wrbdrones$checkAddonDrone(RenderContext context, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return;
        }

        CompoundTag tag = NBTTool.getTag(stack);
        if (!tag.getBoolean("Using") || !tag.getBoolean("Linked")) {
            return;
        }

        DroneEntity drone = EntityFindUtil.findDrone(player.level(), tag.getString("LinkedDrone"));
        if (drone instanceof AddonDroneEntity) {
            ci.cancel();
        }
    }
}
