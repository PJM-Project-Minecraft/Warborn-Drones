package ru.liko.wrbdrones.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.client.overlay.DroneHudOverlay;
import ru.liko.wrbdrones.client.overlay.MavicHudOverlay;
import ru.liko.wrbdrones.client.renderer.AddonDroneRenderer;
import ru.liko.wrbdrones.client.renderer.PlayerDecoyRenderer;
import ru.liko.wrbdrones.client.renderer.RebMiniRenderer;
import ru.liko.wrbdrones.client.renderer.RebRenderer;
import ru.liko.wrbdrones.client.renderer.Shahed136Renderer;
import ru.liko.wrbdrones.registry.ModEntityTypes;

@Mod.EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.MAVIC_DRONE_WITH_DROP.get(), context -> new AddonDroneRenderer<>(context));
        event.registerEntityRenderer(ModEntityTypes.MAVIC_DRONE_NO_DROP.get(), context -> new AddonDroneRenderer<>(context));
        event.registerEntityRenderer(ModEntityTypes.FPV_DRONE.get(), context -> new AddonDroneRenderer<>(context));
        event.registerEntityRenderer(ModEntityTypes.REB.get(), RebRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.REB_MINI.get(), RebMiniRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SHAHED136.get(), Shahed136Renderer::new);
        event.registerEntityRenderer(ModEntityTypes.PLAYER_DECOY.get(), PlayerDecoyRenderer::new);
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        // Регистрируем основной HUD дрона
        // Проверка типа дрона происходит внутри overlay, поэтому регистрируем его независимо
        event.registerBelowAll(DroneHudOverlay.ID, new DroneHudOverlay());
        // Регистрируем специальный HUD для Mavic дронов
        event.registerAboveAll(MavicHudOverlay.ID, new MavicHudOverlay());
    }
}
