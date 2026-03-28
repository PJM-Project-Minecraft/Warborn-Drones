package ru.liko.wrbdrones.network;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

import java.util.Objects;
import java.util.function.Supplier;

public final class ExitDroneControlPacket {

    public ExitDroneControlPacket() {
    }

    public static void encode(ExitDroneControlPacket packet, FriendlyByteBuf buf) {
        // Пакет без параметров
    }

    public static ExitDroneControlPacket decode(FriendlyByteBuf buf) {
        return new ExitDroneControlPacket();
    }

    public static void handle(ExitDroneControlPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> handlePacket(packet, context));
        context.setPacketHandled(true);
    }

    private static void handlePacket(ExitDroneControlPacket packet, NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            return;
        }

        // Проверяем, что игрок держит монитор
        ItemStack stack = sender.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return;
        }

        // Проверяем, что монитор связан и используется
        var tag = stack.getOrCreateTag();
        if (!tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)
                || !tag.getBoolean("Using")) {
            return;
        }

        // Получаем дрон
        String droneId = tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE);
        if (droneId == null || droneId.isEmpty() || droneId.equals("none")) {
            return;
        }

        var drone = com.atsuishio.superbwarfare.tools.EntityFindUtil.findDrone(sender.level(), droneId);
        if (!(drone instanceof AddonDroneEntity addonDrone)) {
            return;
        }

        // Проверяем, что игрок является контроллером дрона
        String controllerId = addonDrone.getEntityData().get(DroneEntity.CONTROLLER);
        if (controllerId == null || controllerId.isEmpty() || controllerId.equals("undefined")) {
            return;
        }

        if (!Objects.equals(sender.getStringUUID(), controllerId)) {
            return;
        }

        // Проверяем, что игрок сидит на дроне
        if (sender.getVehicle() != addonDrone) {
            return;
        }

        // Выходим из управления дроном
        addonDrone.endRemoteControl(sender);
        
        // Также переключаем флаг "Using" в мониторе
        tag.putBoolean("Using", false);
    }
}

