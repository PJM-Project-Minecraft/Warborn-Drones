package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;

import java.util.ArrayList;
import java.util.List;

public record OpenRadioScreenPacket(
        int shahedEntityId,
        int targetX,
        int targetY,
        int targetZ,
        int droneX,
        int droneY,
        int droneZ,
        boolean terrainFollow,
        List<int[]> waypoints,
        double minSpeed,
        double maxSpeed,
        double minAltitude,
        double maxAltitude,
        double maxDistance,
        int maxWaypoints,
        boolean terrainFollowAllowed) implements CustomPacketPayload {

    public static final Type<OpenRadioScreenPacket> TYPE = new Type<>(Wrbdrones.loc("open_radio_screen"));

    public static final StreamCodec<ByteBuf, OpenRadioScreenPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.INT.encode(buf, packet.shahedEntityId);
                ByteBufCodecs.INT.encode(buf, packet.targetX);
                ByteBufCodecs.INT.encode(buf, packet.targetY);
                ByteBufCodecs.INT.encode(buf, packet.targetZ);
                ByteBufCodecs.INT.encode(buf, packet.droneX);
                ByteBufCodecs.INT.encode(buf, packet.droneY);
                ByteBufCodecs.INT.encode(buf, packet.droneZ);
                ByteBufCodecs.BOOL.encode(buf, packet.terrainFollow);
                ByteBufCodecs.INT.encode(buf, packet.waypoints.size());
                for (int[] wp : packet.waypoints) {
                    ByteBufCodecs.INT.encode(buf, wp[0]);
                    ByteBufCodecs.INT.encode(buf, wp[1]);
                    ByteBufCodecs.INT.encode(buf, wp[2]);
                }
                ByteBufCodecs.DOUBLE.encode(buf, packet.minSpeed);
                ByteBufCodecs.DOUBLE.encode(buf, packet.maxSpeed);
                ByteBufCodecs.DOUBLE.encode(buf, packet.minAltitude);
                ByteBufCodecs.DOUBLE.encode(buf, packet.maxAltitude);
                ByteBufCodecs.DOUBLE.encode(buf, packet.maxDistance);
                ByteBufCodecs.INT.encode(buf, packet.maxWaypoints);
                ByteBufCodecs.BOOL.encode(buf, packet.terrainFollowAllowed);
            },
            buf -> {
                int shahedEntityId = ByteBufCodecs.INT.decode(buf);
                int targetX = ByteBufCodecs.INT.decode(buf);
                int targetY = ByteBufCodecs.INT.decode(buf);
                int targetZ = ByteBufCodecs.INT.decode(buf);
                int droneX = ByteBufCodecs.INT.decode(buf);
                int droneY = ByteBufCodecs.INT.decode(buf);
                int droneZ = ByteBufCodecs.INT.decode(buf);
                boolean terrainFollow = ByteBufCodecs.BOOL.decode(buf);
                int count = ByteBufCodecs.INT.decode(buf);
                List<int[]> waypoints = new ArrayList<>(Math.min(count, 16));
                for (int i = 0; i < count; i++) {
                    int x = ByteBufCodecs.INT.decode(buf);
                    int y = ByteBufCodecs.INT.decode(buf);
                    int z = ByteBufCodecs.INT.decode(buf);
                    waypoints.add(new int[]{x, y, z});
                }
                double minSpeed = ByteBufCodecs.DOUBLE.decode(buf);
                double maxSpeed = ByteBufCodecs.DOUBLE.decode(buf);
                double minAltitude = ByteBufCodecs.DOUBLE.decode(buf);
                double maxAltitude = ByteBufCodecs.DOUBLE.decode(buf);
                double maxDistance = ByteBufCodecs.DOUBLE.decode(buf);
                int maxWaypoints = ByteBufCodecs.INT.decode(buf);
                boolean terrainFollowAllowed = ByteBufCodecs.BOOL.decode(buf);
                return new OpenRadioScreenPacket(shahedEntityId, targetX, targetY, targetZ,
                        droneX, droneY, droneZ, terrainFollow, waypoints,
                        minSpeed, maxSpeed, minAltitude, maxAltitude, maxDistance, maxWaypoints, terrainFollowAllowed);
            });

    public static void handler(OpenRadioScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientPacketHandler.handleOpenRadioScreen(
                    packet.shahedEntityId,
                    packet.targetX,
                    packet.targetY,
                    packet.targetZ,
                    packet.droneX,
                    packet.droneY,
                    packet.droneZ,
                    packet.terrainFollow,
                    packet.waypoints,
                    packet.minSpeed,
                    packet.maxSpeed,
                    packet.minAltitude,
                    packet.maxAltitude,
                    packet.maxDistance,
                    packet.maxWaypoints,
                    packet.terrainFollowAllowed);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
