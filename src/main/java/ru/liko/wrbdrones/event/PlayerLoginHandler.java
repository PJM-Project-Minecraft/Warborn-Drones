package ru.liko.wrbdrones.event;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.util.PilotViewAnchors;
import ru.liko.wrbdrones.util.PilotChunkTicket;

/**
 * Обработчик входа игрока в мир.
 * Восстанавливает игрока, если он вышел из мира во время управления дроном.
 */
@EventBusSubscriber(modid = Wrbdrones.MODID)
public class PlayerLoginHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        
        // Ищем дрон, который контролировался этим игроком
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AddonDroneEntity drone) {
                String controllerId = drone.getEntityData().get(DroneEntity.CONTROLLER);
                if (controllerId != null && controllerId.equals(player.getStringUUID())) {
                    // Нашли дрон, который контролировался этим игроком
                    // Восстанавливаем игрока на исходную позицию
                    restorePlayerFromDroneControl(player, drone);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        // Игрок вышел во время управления — снимаем self-chunk ресурсы:
        // якорь вида и форс-загрузку домашнего чанка.
        PilotViewAnchors.clearAnchor(player.getUUID());
        PilotChunkTicket.release(player);
    }

    /**
     * Восстанавливает игрока после выхода из мира во время управления дроном.
     */
    private static void restorePlayerFromDroneControl(ServerPlayer player, AddonDroneEntity drone) {
        // Восстанавливаем состояние игрока
        player.setInvisible(false);
        player.setSilent(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        player.fallDistance = 0.0f;

        // Вызываем endRemoteControl для корректного завершения сессии
        drone.endRemoteControl(player);
    }
}
