package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.Shahed136Entity;

import java.util.ArrayList;
import java.util.List;

public record LaunchShahedPacket(
        int shahedEntityId,
        int targetX,
        int targetY,
        int targetZ,
        float speed,
        float altitude,
        boolean evasiveMode,
        boolean terrainFollow,
        List<int[]> waypoints) implements CustomPacketPayload {

    private static final int ABSOLUTE_MAX_WAYPOINTS = 16;

    public static final Type<LaunchShahedPacket> TYPE = new Type<>(Wrbdrones.loc("launch_shahed"));

    public static final StreamCodec<ByteBuf, LaunchShahedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                if (packet.waypoints.size() > ABSOLUTE_MAX_WAYPOINTS) {
                    throw new IllegalArgumentException("Too many Shahed waypoints: " + packet.waypoints.size());
                }
                ByteBufCodecs.INT.encode(buf, packet.shahedEntityId);
                ByteBufCodecs.INT.encode(buf, packet.targetX);
                ByteBufCodecs.INT.encode(buf, packet.targetY);
                ByteBufCodecs.INT.encode(buf, packet.targetZ);
                ByteBufCodecs.FLOAT.encode(buf, packet.speed);
                ByteBufCodecs.FLOAT.encode(buf, packet.altitude);
                ByteBufCodecs.BOOL.encode(buf, packet.evasiveMode);
                ByteBufCodecs.BOOL.encode(buf, packet.terrainFollow);
                // Промежуточные путевые точки: count + N·(x,y,z). Финальная цель —
                // targetX/Y/Z, сюда НЕ входит (её Shahed добавляет в launch()).
                ByteBufCodecs.INT.encode(buf, packet.waypoints.size());
                for (int[] wp : packet.waypoints) {
                    if (wp == null || wp.length < 3) {
                        throw new IllegalArgumentException("Shahed waypoint must contain x, y and z");
                    }
                    ByteBufCodecs.INT.encode(buf, wp[0]);
                    ByteBufCodecs.INT.encode(buf, wp[1]);
                    ByteBufCodecs.INT.encode(buf, wp[2]);
                }
            },
            buf -> {
                int shahedEntityId = ByteBufCodecs.INT.decode(buf);
                int targetX = ByteBufCodecs.INT.decode(buf);
                int targetY = ByteBufCodecs.INT.decode(buf);
                int targetZ = ByteBufCodecs.INT.decode(buf);
                float speed = ByteBufCodecs.FLOAT.decode(buf);
                float altitude = ByteBufCodecs.FLOAT.decode(buf);
                boolean evasiveMode = ByteBufCodecs.BOOL.decode(buf);
                boolean terrainFollow = ByteBufCodecs.BOOL.decode(buf);
                int count = ByteBufCodecs.INT.decode(buf);
                if (count < 0 || count > ABSOLUTE_MAX_WAYPOINTS) {
                    throw new DecoderException("Invalid Shahed waypoint count: " + count);
                }
                List<int[]> waypoints = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int x = ByteBufCodecs.INT.decode(buf);
                    int y = ByteBufCodecs.INT.decode(buf);
                    int z = ByteBufCodecs.INT.decode(buf);
                    waypoints.add(new int[]{x, y, z});
                }
                return new LaunchShahedPacket(shahedEntityId, targetX, targetY, targetZ,
                        speed, altitude, evasiveMode, terrainFollow, waypoints);
            });

    public static void handler(LaunchShahedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                if (!Float.isFinite(packet.speed()) || !Float.isFinite(packet.altitude())) {
                    return;
                }
                int configuredMaxWaypoints = ServerConfig.SHAHED136_MAX_WAYPOINTS.get();
                if (packet.waypoints().size() > configuredMaxWaypoints) {
                    return;
                }
                Entity entity = serverLevel.getEntity(packet.shahedEntityId);
                if (entity instanceof Shahed136Entity shahed) {
                    if (!shahed.isLaunched()) {
                        // Validate distance to drone
                        if (player.distanceToSqr(shahed) > 64 * 64) {
                            return;
                        }

                        // Validate owner
                        if (shahed.getOwnerUUID() != null && !shahed.getOwnerUUID().equals(player.getUUID())) {
                            return;
                        }

                        // Clamp speed and altitude to config ranges
                        float minSpeed = (float) (ServerConfig.SHAHED136_MIN_SPEED_KMH.get() / 72.0);
                        float maxSpeed = (float) (ServerConfig.SHAHED136_MAX_SPEED_KMH.get() / 72.0);
                        float clampedSpeed = Mth.clamp(packet.speed, minSpeed, maxSpeed);

                        float minAlt = ServerConfig.SHAHED136_MIN_ALTITUDE.get().floatValue();
                        float maxAlt = ServerConfig.SHAHED136_MAX_ALTITUDE.get().floatValue();
                        float clampedAlt = Mth.clamp(packet.altitude, minAlt, maxAlt);

                        shahed.setTargetPos(packet.targetX, packet.targetY, packet.targetZ);
                        shahed.setSetSpeed(clampedSpeed);
                        shahed.setSetAltitude(clampedAlt);
                        shahed.setEvasiveMode(packet.evasiveMode);
                        shahed.setTerrainFollow(packet.terrainFollow
                                && ServerConfig.SHAHED136_TERRAIN_FOLLOW_ALLOWED.get());

                        // Промежуточные путевые точки → Vec3 (финал добавится в launch()).
                        List<Vec3> via = new ArrayList<>(packet.waypoints.size());
                        for (int[] wp : packet.waypoints) {
                            via.add(new Vec3(wp[0], wp[1], wp[2]));
                        }
                        shahed.setWaypoints(via);

                        shahed.launch();
                    }
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
