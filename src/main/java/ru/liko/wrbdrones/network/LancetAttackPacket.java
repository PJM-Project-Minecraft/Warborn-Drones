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
import ru.liko.wrbdrones.entity.ZalaLancetEntity;

import java.util.UUID;

public record LancetAttackPacket(UUID droneId) implements CustomPacketPayload {
    public static final Type<LancetAttackPacket> TYPE = new Type<>(Wrbdrones.loc("lancet_attack"));

    public static final StreamCodec<ByteBuf, LancetAttackPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString),
            LancetAttackPacket::droneId,
            LancetAttackPacket::new);

    public static void handler(LancetAttackPacket packet, IPayloadContext ctx) {
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

            if (lancet.hasTarget()) {
                lancet.setTerminalAttack(true);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
