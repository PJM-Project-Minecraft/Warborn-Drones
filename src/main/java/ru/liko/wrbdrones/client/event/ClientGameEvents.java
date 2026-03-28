package ru.liko.wrbdrones.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientGameEvents {

    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.getEntity() instanceof AbstractClientPlayer player) {
            // Check if player is riding a drone
            // We want to hide the player ONLY if they are riding an AddonDroneEntity
            if (player.getVehicle() instanceof AddonDroneEntity) {
                // Cancel rendering of the player completely so they don't appear floating above
                // the drone
                event.setCanceled(true);
            }
        }
    }

}
