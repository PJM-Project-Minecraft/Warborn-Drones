package ru.liko.wrbdrones.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.client.overlay.DroneHudOverlay;
import ru.liko.wrbdrones.client.overlay.MavicHudOverlay;
import ru.liko.wrbdrones.client.renderer.AddonDroneRenderer;
import ru.liko.wrbdrones.client.renderer.PlayerDecoyRenderer;
import ru.liko.wrbdrones.client.renderer.RebMiniRenderer;
import ru.liko.wrbdrones.client.renderer.RebRenderer;
import ru.liko.wrbdrones.client.renderer.Shahed136Renderer;
import ru.liko.wrbdrones.registry.ModEntityTypes;

@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.MAVIC_DRONE_WITH_DROP.get(),
                context -> new AddonDroneRenderer<>(context));
        event.registerEntityRenderer(ModEntityTypes.MAVIC_DRONE_NO_DROP.get(),
                context -> new AddonDroneRenderer<>(context));
        event.registerEntityRenderer(ModEntityTypes.FPV_DRONE.get(), context -> new AddonDroneRenderer<>(context));
        event.registerEntityRenderer(ModEntityTypes.REB.get(), RebRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.REB_MINI.get(), RebMiniRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SHAHED136.get(), Shahed136Renderer::new);
        event.registerEntityRenderer(ModEntityTypes.PLAYER_DECOY.get(), PlayerDecoyRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Регистрируем основной HUD дрона
        event.registerBelow(VanillaGuiLayers.CROSSHAIR, DroneHudOverlay.ID, DroneHudOverlay::render);
        // Регистрируем специальный HUD для Mavic дронов
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, MavicHudOverlay.ID, MavicHudOverlay::render);
    }
}
