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

/** Курс задаётся точкой в мире: клиент проецирует курсор по ЛКМ в мировую точку и шлёт её сюда. */
public record LancetCourseCommandPacket(UUID droneId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<LancetCourseCommandPacket> TYPE = new Type<>(Wrbdrones.loc("lancet_course_command"));

    public static final StreamCodec<ByteBuf, LancetCourseCommandPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString),
            LancetCourseCommandPacket::droneId,
            ByteBufCodecs.DOUBLE,
            LancetCourseCommandPacket::x,
            ByteBufCodecs.DOUBLE,
            LancetCourseCommandPacket::y,
            ByteBufCodecs.DOUBLE,
            LancetCourseCommandPacket::z,
            LancetCourseCommandPacket::new);

    public static void handler(LancetCourseCommandPacket packet, IPayloadContext ctx) {
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

            lancet.setCourseCommand(new Vec3(packet.x(), packet.y(), packet.z()));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
