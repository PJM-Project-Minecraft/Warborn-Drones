package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.Shahed136Entity;

public record LaunchShahedPacket(
        int shahedEntityId,
        int targetX,
        int targetY,
        int targetZ,
        float speed,
        float altitude,
        boolean evasiveMode) implements CustomPacketPayload {

    public static final Type<LaunchShahedPacket> TYPE = new Type<>(Wrbdrones.loc("launch_shahed"));

    public static final StreamCodec<ByteBuf, LaunchShahedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.INT.encode(buf, packet.shahedEntityId);
                ByteBufCodecs.INT.encode(buf, packet.targetX);
                ByteBufCodecs.INT.encode(buf, packet.targetY);
                ByteBufCodecs.INT.encode(buf, packet.targetZ);
                ByteBufCodecs.FLOAT.encode(buf, packet.speed);
                ByteBufCodecs.FLOAT.encode(buf, packet.altitude);
                ByteBufCodecs.BOOL.encode(buf, packet.evasiveMode);
            },
            buf -> new LaunchShahedPacket(
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf)));

    public static void handler(LaunchShahedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            if (player.level() instanceof ServerLevel serverLevel) {
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
