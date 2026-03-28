package ru.liko.wrbdrones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import ru.liko.wrbdrones.entity.Shahed136Entity;

import java.util.function.Supplier;

public class LaunchShahedPacket {

    private final int shahedEntityId;
    private final int targetX;
    private final int targetY;
    private final int targetZ;
    private final float speed;
    private final float altitude;
    private final boolean evasiveMode;

    public LaunchShahedPacket(int shahedEntityId, int targetX, int targetY, int targetZ, 
                              float speed, float altitude, boolean evasiveMode) {
        this.shahedEntityId = shahedEntityId;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.speed = speed;
        this.altitude = altitude;
        this.evasiveMode = evasiveMode;
    }

    public LaunchShahedPacket(FriendlyByteBuf buf) {
        this.shahedEntityId = buf.readInt();
        this.targetX = buf.readInt();
        this.targetY = buf.readInt();
        this.targetZ = buf.readInt();
        this.speed = buf.readFloat();
        this.altitude = buf.readFloat();
        this.evasiveMode = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(shahedEntityId);
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
        buf.writeFloat(speed);
        buf.writeFloat(altitude);
        buf.writeBoolean(evasiveMode);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(shahedEntityId);
                if (entity instanceof Shahed136Entity shahed) {
                    if (!shahed.isLaunched()) {
                        shahed.setTargetPos(targetX, targetY, targetZ);
                        shahed.setSetSpeed(speed);
                        shahed.setSetAltitude(altitude);
                        shahed.setEvasiveMode(evasiveMode);
                        shahed.launch();
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
