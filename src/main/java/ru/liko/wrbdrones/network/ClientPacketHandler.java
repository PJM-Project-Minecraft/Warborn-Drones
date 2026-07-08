package ru.liko.wrbdrones.network;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ru.liko.wrbdrones.client.screen.LancetLaunchPlatformScreen;
import ru.liko.wrbdrones.client.screen.RadioScreen;
import ru.liko.wrbdrones.client.sound.ShahedSoundHandler;

import java.util.List;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleOpenRadioScreen(int shahedEntityId, int targetX, int targetY, int targetZ, int droneX,
            int droneY, int droneZ, boolean terrainFollow, List<int[]> waypoints,
            double minSpeed, double maxSpeed, double minAltitude, double maxAltitude,
            double maxDistance, int maxWaypoints, boolean terrainFollowAllowed) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new RadioScreen(shahedEntityId, targetX, targetY, targetZ,
                    droneX, droneY, droneZ, terrainFollow, waypoints,
                    minSpeed, maxSpeed, minAltitude, maxAltitude, maxDistance, maxWaypoints, terrainFollowAllowed));
        });
    }

    public static void handleShahedExplode(UUID droneId) {
        ShahedSoundHandler.onDroneExploded(droneId);
    }

    public static void handleOpenLancetPlatformScreen(int platformEntityId, boolean loaded) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance()
                .setScreen(new LancetLaunchPlatformScreen(platformEntityId, loaded)));
    }
}
