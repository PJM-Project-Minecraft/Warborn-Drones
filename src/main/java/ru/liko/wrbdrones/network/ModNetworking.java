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
        registrar.playToServer(MavicDroneFirePacket.TYPE, MavicDroneFirePacket.STREAM_CODEC,
                MavicDroneFirePacket::handler);
        registrar.playToServer(LancetInputPacket.TYPE, LancetInputPacket.STREAM_CODEC,
                LancetInputPacket::handler);
        registrar.playToServer(LancetCourseCommandPacket.TYPE, LancetCourseCommandPacket.STREAM_CODEC,
                LancetCourseCommandPacket::handler);
        registrar.playToServer(LancetTargetPacket.TYPE, LancetTargetPacket.STREAM_CODEC,
                LancetTargetPacket::handler);
        registrar.playToServer(LancetStatePacket.TYPE, LancetStatePacket.STREAM_CODEC,
                LancetStatePacket::handler);
        registrar.playToServer(LancetAttackPacket.TYPE, LancetAttackPacket.STREAM_CODEC,
                LancetAttackPacket::handler);
        registrar.playToServer(LancetPlatformActionPacket.TYPE, LancetPlatformActionPacket.STREAM_CODEC,
                LancetPlatformActionPacket::handler);
        registrar.playToServer(ExitDroneControlPacket.TYPE, ExitDroneControlPacket.STREAM_CODEC,
                ExitDroneControlPacket::handler);
        registrar.playToServer(LaunchShahedPacket.TYPE, LaunchShahedPacket.STREAM_CODEC, LaunchShahedPacket::handler);
        registrar.playToServer(StartDroneAssemblyPacket.TYPE, StartDroneAssemblyPacket.STREAM_CODEC,
                StartDroneAssemblyPacket::handler);
        registrar.playToServer(CancelDroneAssemblyPacket.TYPE, CancelDroneAssemblyPacket.STREAM_CODEC,
                CancelDroneAssemblyPacket::handler);

        // Client-bound packets (S2C)
        registrar.playToClient(OpenRadioScreenPacket.TYPE, OpenRadioScreenPacket.STREAM_CODEC,
                OpenRadioScreenPacket::handler);
        registrar.playToClient(OpenLancetPlatformScreenPacket.TYPE, OpenLancetPlatformScreenPacket.STREAM_CODEC,
                OpenLancetPlatformScreenPacket::handler);
        registrar.playToClient(ShahedExplodePacket.TYPE, ShahedExplodePacket.STREAM_CODEC,
                ShahedExplodePacket::handler);
    }
}
