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
import ru.liko.wrbdrones.entity.AddonDroneEntity;

import java.util.Objects;
import java.util.UUID;

public record DroneSignalLostPacket(UUID droneId) implements CustomPacketPayload {

    public static final Type<DroneSignalLostPacket> TYPE = new Type<>(Wrbdrones.loc("drone_signal_lost"));

    public static final StreamCodec<ByteBuf, DroneSignalLostPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString),
            DroneSignalLostPacket::droneId,
            DroneSignalLostPacket::new);

    public static void handler(DroneSignalLostPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) {
                return;
            }

            ServerLevel level = sender.serverLevel();
            UUID droneIdUUID = packet.droneId();
            Entity entity = level.getEntity(droneIdUUID);
            if (!(entity instanceof AddonDroneEntity drone) || drone.isRemoved()) {
                return;
            }

            if (!isAuthorized(sender, drone)) {
                return;
            }

            drone.handleSignalLoss(sender);
        });
    }

    private static boolean isAuthorized(ServerPlayer player, AddonDroneEntity drone) {
        String controllerId = drone.getEntityData().get(DroneEntity.CONTROLLER);
        if (controllerId != null) {
            controllerId = controllerId.trim();
        }

        if (controllerId != null && !controllerId.isEmpty()
                && !controllerId.equalsIgnoreCase("undefined")
                && !controllerId.equalsIgnoreCase("none")
                && Objects.equals(player.getStringUUID(), controllerId)) {
            return true;
        }

        ItemStack heldStack = player.getMainHandItem();
        var monitorItem = Objects.requireNonNull(ModItems.MONITOR.get(), "monitor");
        if (!heldStack.is(monitorItem)) {
            return false;
        }

        var tag = NBTTool.getTag(heldStack);
        if (!tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)
                || !tag.getBoolean("Using")) {
            return false;
        }

        return drone.getStringUUID().equals(tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
