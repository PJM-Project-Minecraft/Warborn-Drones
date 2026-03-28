package ru.liko.wrbdrones.network;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import ru.liko.wrbdrones.client.screen.RadioScreen;
import ru.liko.wrbdrones.client.sound.ShahedSoundHandler;

import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleOpenRadioScreen(int shahedEntityId, int targetX, int targetY, int targetZ, int droneX,
            int droneY, int droneZ) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance()
                    .setScreen(new RadioScreen(shahedEntityId, targetX, targetY, targetZ, droneX, droneY, droneZ));
        });
    }

    public static void handleShahedExplode(UUID droneId) {
        ShahedSoundHandler.onDroneExploded(droneId);
    }
}
