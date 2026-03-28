package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;

public record ShahedExplodePacket(long uuidMost, long uuidLeast) implements CustomPacketPayload {

    public static final Type<ShahedExplodePacket> TYPE = new Type<>(Wrbdrones.loc("shahed_explode"));

    public static final StreamCodec<ByteBuf, ShahedExplodePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ShahedExplodePacket::uuidMost,
            ByteBufCodecs.VAR_LONG, ShahedExplodePacket::uuidLeast,
            ShahedExplodePacket::new);

    public static void handler(ShahedExplodePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            java.util.UUID uuid = new java.util.UUID(packet.uuidMost, packet.uuidLeast);
            ClientPacketHandler.handleShahedExplode(uuid);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
