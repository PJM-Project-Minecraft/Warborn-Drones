package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;

import java.util.UUID;

public record LancetTargetPacket(
        UUID droneId,
        int action,
        double x,
        double y,
        double z,
        String targetEntityUuid) implements CustomPacketPayload {

    public static final int ACTION_CLEAR = 0;
    public static final int ACTION_POINT = 1;
    public static final int ACTION_ENTITY = 2;
    public static final int ACTION_TERMINAL = 3;

    public static final Type<LancetTargetPacket> TYPE = new Type<>(Wrbdrones.loc("lancet_target"));

    public static final StreamCodec<ByteBuf, LancetTargetPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.droneId.toString());
                ByteBufCodecs.INT.encode(buf, packet.action);
                buf.writeDouble(packet.x);
                buf.writeDouble(packet.y);
                buf.writeDouble(packet.z);
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.targetEntityUuid == null ? "" : packet.targetEntityUuid);
            },
            buf -> new LancetTargetPacket(
                    UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buf)),
                    ByteBufCodecs.INT.decode(buf),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    ByteBufCodecs.STRING_UTF8.decode(buf)));

    public static LancetTargetPacket clear(UUID droneId) {
        return new LancetTargetPacket(droneId, ACTION_CLEAR, 0.0, 0.0, 0.0, "");
    }

    public static LancetTargetPacket point(UUID droneId, Vec3 pos) {
        return new LancetTargetPacket(droneId, ACTION_POINT, pos.x, pos.y, pos.z, "");
    }

    public static LancetTargetPacket entity(UUID droneId, UUID targetUuid, Vec3 fallbackPos) {
        return new LancetTargetPacket(droneId, ACTION_ENTITY, fallbackPos.x, fallbackPos.y, fallbackPos.z,
                targetUuid.toString());
    }

    public static LancetTargetPacket terminal(UUID droneId) {
        return new LancetTargetPacket(droneId, ACTION_TERMINAL, 0.0, 0.0, 0.0, "");
    }

    public static void handler(LancetTargetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) {
                return;
            }

            Entity entity = LancetInputPacket.findEntity(sender, packet.droneId());
            if (!(entity instanceof ZalaLancetEntity lancet) || lancet.isRemoved()) {
                return;
            }
            if (!LancetInputPacket.isAuthorized(sender, lancet)) {
                return;
            }

            switch (packet.action()) {
                case ACTION_CLEAR -> lancet.clearTarget();
                case ACTION_POINT -> lancet.setPointTarget(new Vec3(packet.x(), packet.y(), packet.z()));
                case ACTION_ENTITY -> {
                    try {
                        UUID targetUuid = UUID.fromString(packet.targetEntityUuid());
                        lancet.setEntityTarget(targetUuid, new Vec3(packet.x(), packet.y(), packet.z()));
                    } catch (IllegalArgumentException ignored) {
                        lancet.setPointTarget(new Vec3(packet.x(), packet.y(), packet.z()));
                    }
                }
                case ACTION_TERMINAL -> lancet.setTerminalAttack(true);
                default -> {
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
