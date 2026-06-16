package ru.liko.wrbdrones.network;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.NBTTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;

import java.util.Objects;
import java.util.UUID;

public record LancetInputPacket(UUID droneId, boolean yawLeft, boolean yawRight) implements CustomPacketPayload {

    public static final Type<LancetInputPacket> TYPE = new Type<>(Wrbdrones.loc("lancet_input"));

    public static final StreamCodec<ByteBuf, LancetInputPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString),
            LancetInputPacket::droneId,
            ByteBufCodecs.BOOL,
            LancetInputPacket::yawLeft,
            ByteBufCodecs.BOOL,
            LancetInputPacket::yawRight,
            LancetInputPacket::new);

    public static void handler(LancetInputPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) {
                return;
            }

            Entity entity = findEntity(sender, packet.droneId());
            if (!(entity instanceof ZalaLancetEntity lancet) || lancet.isRemoved()) {
                return;
            }
            if (!isAuthorized(sender, lancet)) {
                return;
            }

            lancet.setRudderInput(packet.yawLeft(), packet.yawRight());
        });
    }

    static Entity findEntity(ServerPlayer sender, UUID uuid) {
        for (ServerLevel level : sender.server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    static boolean isAuthorized(ServerPlayer player, ZalaLancetEntity lancet) {
        String controllerId = lancet.getEntityData().get(DroneEntity.CONTROLLER);
        if (controllerId != null) {
            controllerId = controllerId.trim();
        }

        if (controllerId == null || controllerId.isEmpty()
                || controllerId.equalsIgnoreCase("undefined")
                || controllerId.equalsIgnoreCase("none")
                || !Objects.equals(player.getStringUUID(), controllerId)) {
            return false;
        }

        ItemStack heldStack = player.getMainHandItem();
        var monitorItem = Objects.requireNonNull(ModItems.MONITOR.get(), "monitor");
        if (!heldStack.is(monitorItem)) {
            return false;
        }

        var tag = NBTTool.getTag(heldStack);
        return tag.getBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED)
                && tag.getBoolean("Using")
                && lancet.getStringUUID()
                        .equals(tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
