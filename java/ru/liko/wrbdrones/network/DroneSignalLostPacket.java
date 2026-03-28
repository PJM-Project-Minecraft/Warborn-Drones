package ru.liko.wrbdrones.network;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

public final class DroneSignalLostPacket {

    private final UUID droneId;

    public DroneSignalLostPacket(@NotNull UUID droneId) {
        this.droneId = Objects.requireNonNull(droneId, "droneId");
    }

    public @NotNull UUID getDroneId() {
        return droneId;
    }

    public static void encode(@NotNull DroneSignalLostPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.getDroneId());
    }

    public static @NotNull DroneSignalLostPacket decode(FriendlyByteBuf buf) {
        UUID id = Objects.requireNonNull(buf.readUUID(), "droneId");
        return new DroneSignalLostPacket(id);
    }

    public static void handle(DroneSignalLostPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handlePacket(packet, context));
        context.setPacketHandled(true);
    }

    private static void handlePacket(DroneSignalLostPacket packet, NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            return;
        }

        ServerLevel level = sender.serverLevel();
        UUID droneId = packet.getDroneId();
        Entity entity = level.getEntity(droneId);
        if (!(entity instanceof AddonDroneEntity drone) || drone.isRemoved()) {
            return;
        }

        if (!isAuthorized(sender, drone)) {
            return;
        }

        drone.handleSignalLoss(sender);
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

        var tag = heldStack.getOrCreateTag();
        if (!tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)
                || !tag.getBoolean("Using")) {
            return false;
        }

        return drone.getStringUUID().equals(tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE));
    }
}

