package ru.liko.wrbdrones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenRadioScreenPacket {

    private final int shahedEntityId;
    private final int targetX;
    private final int targetY;
    private final int targetZ;
    private final int droneX;
    private final int droneY;
    private final int droneZ;

    public OpenRadioScreenPacket(int shahedEntityId, int targetX, int targetY, int targetZ, int droneX, int droneY, int droneZ) {
        this.shahedEntityId = shahedEntityId;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.droneX = droneX;
        this.droneY = droneY;
        this.droneZ = droneZ;
    }

    public OpenRadioScreenPacket(FriendlyByteBuf buf) {
        this.shahedEntityId = buf.readInt();
        this.targetX = buf.readInt();
        this.targetY = buf.readInt();
        this.targetZ = buf.readInt();
        this.droneX = buf.readInt();
        this.droneY = buf.readInt();
        this.droneZ = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(shahedEntityId);
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeInt(droneX);
        buf.writeInt(droneY);
        buf.writeInt(droneZ);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientPacketHandler.handleOpenRadioScreen(shahedEntityId, targetX, targetY, targetZ, droneX, droneY, droneZ);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
