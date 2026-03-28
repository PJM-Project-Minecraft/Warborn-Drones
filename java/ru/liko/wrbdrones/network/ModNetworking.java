package ru.liko.wrbdrones.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.liko.wrbdrones.Wrbdrones;

import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

public final class ModNetworking {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Wrbdrones.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private ModNetworking() {
    }

    private static int nextId() {
        return packetId++;
    }

    public static void init() {
        CHANNEL.registerMessage(
                nextId(),
                DroneSignalLostPacket.class,
                DroneSignalLostPacket::encode,
                DroneSignalLostPacket::decode,
                DroneSignalLostPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                ExitDroneControlPacket.class,
                ExitDroneControlPacket::encode,
                ExitDroneControlPacket::decode,
                ExitDroneControlPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                OpenRadioScreenPacket.class,
                OpenRadioScreenPacket::encode,
                OpenRadioScreenPacket::new,
                OpenRadioScreenPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                LaunchShahedPacket.class,
                LaunchShahedPacket::encode,
                LaunchShahedPacket::new,
                LaunchShahedPacket::handle
        );
    }

    public static void reportSignalLost(UUID droneId) {
        CHANNEL.sendToServer(new DroneSignalLostPacket(droneId));
    }

    public static void requestExitDroneControl() {
        CHANNEL.sendToServer(new ExitDroneControlPacket());
    }

    public static <T> void sendToServer(T packet) {
        CHANNEL.sendToServer(packet);
    }

    public static <T> void sendToPlayer(ServerPlayer player, T packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    
    public static <T> void sendToAllNear(double x, double y, double z, double radius, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, T packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, radius, dimension)), packet);
    }
    
    public static <T> void sendToDimension(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, T packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }
    
    public static <T> void sendToAll(T packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}

