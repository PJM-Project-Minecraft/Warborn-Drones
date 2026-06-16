package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.LancetLaunchPlatformEntity;

public record LancetPlatformActionPacket(int platformEntityId, int action) implements CustomPacketPayload {
    public static final int ACTION_LAUNCH = 0;

    public static final Type<LancetPlatformActionPacket> TYPE = new Type<>(Wrbdrones.loc("lancet_platform_action"));

    public static final StreamCodec<ByteBuf, LancetPlatformActionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            LancetPlatformActionPacket::platformEntityId,
            ByteBufCodecs.INT,
            LancetPlatformActionPacket::action,
            LancetPlatformActionPacket::new);

    public static void handler(LancetPlatformActionPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            Entity entity = player.level().getEntity(packet.platformEntityId());
            if (!(entity instanceof LancetLaunchPlatformEntity platform) || platform.isRemoved()) {
                return;
            }
            if (player.distanceToSqr(platform) > 64.0) {
                return;
            }

            if (packet.action() == ACTION_LAUNCH) {
                platform.launchLoadedLancet(player);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
