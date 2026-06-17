package ru.liko.wrbdrones.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Обрывает управление дроном, если тело пилота умирает во время полёта.
 *
 * <p>В self-chunk режиме тело пилота остаётся на месте и уязвимо. Если его убивают
 * во время управления, управление должно прерваться (как при потере сигнала), а дрон
 * остаётся в мире. Завершение через {@link AddonDroneEntity#endRemoteControl} снимает
 * якорь обзора и форс-загрузку домашнего чанка, иначе они «зависли» бы после смерти.</p>
 */
@EventBusSubscriber(modid = Wrbdrones.MODID)
public final class PilotDeathHandler {

    private PilotDeathHandler() {
    }

    @SubscribeEvent
    public static void onPilotDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
        if (drone instanceof AddonDroneEntity addonDrone) {
            addonDrone.endRemoteControl(player);
        }
    }
}
