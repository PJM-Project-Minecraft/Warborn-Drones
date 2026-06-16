package ru.liko.wrbdrones.client.event;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientGameEvents {

    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.getEntity() instanceof AbstractClientPlayer player) {
            if (player.getVehicle() instanceof AddonDroneEntity) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * Фиксируем FOV на базовом значении из настроек, пока игрок управляет WRB дроном.
     * Иначе спектаторский режим меняет FOV при движении.
     */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) return;

        var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean("Linked") || !tag.getBoolean("Using")) return;

        DroneEntity drone = EntityFindUtil.findDrone(mc.player.level(),
                tag.getString("LinkedDrone"));
        if (!(drone instanceof AddonDroneEntity)) return;

        // Возвращаем базовый FOV из настроек без модификаторов скорости/режима
        event.setFOV(mc.options.fov().get());
    }
}
