package ru.liko.wrbdrones.client;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

import java.util.function.Consumer;

/**
 * Клиентская инициализация: перехватывает playEngineSound для AddonDroneEntity,
 * чтобы наша DroneSoundHandler воспроизводила звук вместо стандартного VehicleSoundInstance.
 */
@EventBusSubscriber(modid = Wrbdrones.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DroneSoundSetup {

    private DroneSoundSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(DroneSoundSetup::wrapEngineSound);
    }

    private static void wrapEngineSound() {
        Consumer<VehicleEntity> original = VehicleEntity.playEngineSound;
        VehicleEntity.playEngineSound = vehicle -> {
            if (vehicle instanceof AddonDroneEntity) {
                return;
            }
            original.accept(vehicle);
        };
    }
}
