package ru.liko.wrbdrones.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ru.liko.wrbdrones.client.screen.RadioScreen;

@OnlyIn(Dist.CLIENT)
public class ClientPacketHandler {

    public static void handleOpenRadioScreen(int shahedEntityId, int targetX, int targetY, int targetZ, int droneX, int droneY, int droneZ) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new RadioScreen(shahedEntityId, targetX, targetY, targetZ, droneX, droneY, droneZ));
        });
    }
}
