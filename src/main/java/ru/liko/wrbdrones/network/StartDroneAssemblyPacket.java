package ru.liko.wrbdrones.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.menu.DroneAssemblyMenu;

public record StartDroneAssemblyPacket(int containerId, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<StartDroneAssemblyPacket> TYPE = new Type<>(Wrbdrones.loc("start_drone_assembly"));

    public static final StreamCodec<ByteBuf, StartDroneAssemblyPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            StartDroneAssemblyPacket::containerId,
            ResourceLocation.STREAM_CODEC,
            StartDroneAssemblyPacket::recipeId,
            StartDroneAssemblyPacket::new);

    public static void handler(StartDroneAssemblyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof DroneAssemblyMenu menu) || menu.containerId != packet.containerId()) {
                return;
            }
            menu.startAssembly(packet.recipeId(), player);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
