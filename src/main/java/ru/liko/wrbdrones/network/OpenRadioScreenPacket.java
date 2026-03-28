package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;

public record OpenRadioScreenPacket(
        int shahedEntityId,
        int targetX,
        int targetY,
        int targetZ,
        int droneX,
        int droneY,
        int droneZ) implements CustomPacketPayload {

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
            },
            buf -> new OpenRadioScreenPacket(
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf)));

    public static void handler(OpenRadioScreenPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientPacketHandler.handleOpenRadioScreen(
                    packet.shahedEntityId,
                    packet.targetX,
                    packet.targetY,
                    packet.targetZ,
                    packet.droneX,
                    packet.droneY,
                    packet.droneZ);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
