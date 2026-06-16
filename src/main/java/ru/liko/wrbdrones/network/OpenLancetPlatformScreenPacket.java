package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;

public record OpenLancetPlatformScreenPacket(int platformEntityId, boolean loaded) implements CustomPacketPayload {
    public static final Type<OpenLancetPlatformScreenPacket> TYPE = new Type<>(
            Wrbdrones.loc("open_lancet_platform_screen"));

    public static final StreamCodec<ByteBuf, OpenLancetPlatformScreenPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            OpenLancetPlatformScreenPacket::platformEntityId,
            ByteBufCodecs.BOOL,
            OpenLancetPlatformScreenPacket::loaded,
            OpenLancetPlatformScreenPacket::new);

    public static void handler(OpenLancetPlatformScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientPacketHandler.handleOpenLancetPlatformScreen(
                packet.platformEntityId(), packet.loaded()));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
