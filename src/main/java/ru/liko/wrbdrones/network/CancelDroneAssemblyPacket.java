package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.menu.DroneAssemblyMenu;

public record CancelDroneAssemblyPacket(int containerId) implements CustomPacketPayload {
    public static final Type<CancelDroneAssemblyPacket> TYPE = new Type<>(Wrbdrones.loc("cancel_drone_assembly"));

    public static final StreamCodec<ByteBuf, CancelDroneAssemblyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            CancelDroneAssemblyPacket::containerId,
            CancelDroneAssemblyPacket::new);

    public static void handler(CancelDroneAssemblyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof DroneAssemblyMenu menu) || menu.containerId != packet.containerId()) {
                return;
            }
            menu.cancelAssembly();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
