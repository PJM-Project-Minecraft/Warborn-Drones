package ru.liko.wrbdrones.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {

    private static PayloadRegistrar registrar;

    private ModNetworking() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        registrar = event.registrar("1");

        // Server-bound packets (C2S)
        registrar.playToServer(DroneSignalLostPacket.TYPE, DroneSignalLostPacket.STREAM_CODEC,
                DroneSignalLostPacket::handler);
        registrar.playToServer(ExitDroneControlPacket.TYPE, ExitDroneControlPacket.STREAM_CODEC,
                ExitDroneControlPacket::handler);
        registrar.playToServer(LaunchShahedPacket.TYPE, LaunchShahedPacket.STREAM_CODEC, LaunchShahedPacket::handler);

        // Client-bound packets (S2C)
        registrar.playToClient(OpenRadioScreenPacket.TYPE, OpenRadioScreenPacket.STREAM_CODEC,
                OpenRadioScreenPacket::handler);
        registrar.playToClient(ShahedExplodePacket.TYPE, ShahedExplodePacket.STREAM_CODEC,
                ShahedExplodePacket::handler);
    }
}
