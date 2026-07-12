package ru.liko.wrbdrones.event;

import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.misc.MonitorItem;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneNoDropEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;
import ru.liko.wrbdrones.util.DroneChunkLoader;
import ru.liko.wrbdrones.util.SignalCalculator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Каждый серверный тик держит загруженными чанки дронов, которых пилотируют или на
 * которых наведён привязанный монитор в руке, чтобы дрон тикал и стримился пилоту
 * вдали от игроков.
 *
 * <p>Загрузку делает {@link DroneChunkLoader} собственным region-ticket'ом вокруг
 * дрона — НЕ трогая player-ticket и учёт игроков в {@code DistanceManager} (прежний
 * подход с подменой секции игрока рассинхронизировал общий учёт и ломал прогрузку у
 * ВСЕХ). Каждый тик собираем множество «активных» дронов и снимаем тикеты у всех
 * остальных ({@link DroneChunkLoader#releaseAllExcept}) — единая точка снятия для всех
 * случаев: монитор убран, игрок вышел, дрон удалён.</p>
 */
@EventBusSubscriber(modid = Wrbdrones.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DroneChunkTickHandler {

    private static int signalCheckTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        signalCheckTickCounter++;
        boolean checkSignal = ServerConfig.SIGNAL_SERVER_CUTOFF_ENABLED.get()
                && signalCheckTickCounter >= ServerConfig.SIGNAL_SERVER_CHECK_INTERVAL_TICKS.get();
        if (checkSignal) signalCheckTickCounter = 0;

        // Грузим вокруг дрона столько же чанков, сколько игрок грузит вокруг себя.
        int viewDistance = event.getServer().getPlayerList().getViewDistance();

        Set<UUID> activeDrones = new HashSet<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            AddonDroneEntity drone = findLinkedDrone(player);
            if (drone == null) {
                continue;
            }
            DroneChunkLoader.keepLoaded(drone, viewDistance);
            activeDrones.add(drone.getUUID());
            if (checkSignal) {
                checkServerSignalCutoff(player, drone);
            }
        }
        // Дрон, которого в этот тик никто не держит, теряет тикет и выгружается.
        DroneChunkLoader.releaseAllExcept(activeDrones);
    }

    /**
     * Авторитарная серверная проверка качества сигнала. Если итоговый сигнал ниже
     * настраиваемого порога, сервер сам инициирует {@code handleSignalLoss(player, false)}.
     * Защищает от модифицированных клиентов, не отправляющих {@code DroneSignalLostPacket}.
     */
    private static void checkServerSignalCutoff(ServerPlayer player, AddonDroneEntity drone) {
        Vec3 operatorPos = drone.getOperatorPosition();
        if (operatorPos == null) operatorPos = player.position();

        double maxDistance;
        double signalLossDistance;
        if (drone instanceof MavicDroneWithDropEntity || drone instanceof MavicDroneNoDropEntity) {
            maxDistance = ServerConfig.MAVIC_MAX_DISTANCE.get();
            signalLossDistance = ServerConfig.MAVIC_SIGNAL_LOSS_DISTANCE.get();
        } else if (drone instanceof ZalaLancetEntity) {
            maxDistance = ServerConfig.LANCET_MAX_DISTANCE.get();
            signalLossDistance = -1.0;
        } else {
            maxDistance = ServerConfig.FPV_MAX_DISTANCE.get();
            signalLossDistance = -1.0;
        }

        SignalCalculator.SignalResult sig = SignalCalculator.computeUncached(
                drone.level(), operatorPos, drone, maxDistance, signalLossDistance);
        double quality = sig.finalQuality();

        // Приоритет destroy: при качестве <= destroy_threshold (по умолчанию 0.0)
        // дрон самоуничтожается, как при ЛКМ-камикадзе.
        if (ServerConfig.SIGNAL_DESTROY_ON_ZERO_ENABLED.get()
                && quality <= ServerConfig.SIGNAL_DESTROY_THRESHOLD.get()) {
            drone.handleSignalLoss(player, true);
            return;
        }

        if (quality <= ServerConfig.SIGNAL_SERVER_CUTOFF_THRESHOLD.get()) {
            drone.handleSignalLoss(player, false);
        }
    }

    /**
     * Возвращает дрон, который игрок сейчас держит загруженным: либо активно пилотирует
     * (есть якорь вида), либо в главной руке привязанный к дрону монитор. Иначе {@code null}.
     * Работой с чанками здесь не занимаемся — только идентификация дрона.
     */
    private static AddonDroneEntity findLinkedDrone(ServerPlayer player) {
        // Активное пилотирование — дрон известен напрямую по якорю (ссылка на сущность).
        Entity anchor = ru.liko.wrbdrones.util.PilotViewAnchors.getAnchorDrone(player.getUUID());
        if (anchor instanceof AddonDroneEntity anchorDrone) {
            return anchorDrone;
        }

        // Иначе — монитор в главной руке, привязанный к дрону.
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.MONITOR.get())) {
            return null;
        }
        var tag = NBTTool.getTag(mainHand);
        if (!tag.getBoolean(MonitorItem.LINKED)) {
            return null;
        }
        String linkedDroneId = tag.getString(MonitorItem.LINKED_DRONE);
        if (linkedDroneId == null || linkedDroneId.isEmpty() || linkedDroneId.equals("none")) {
            return null;
        }
        try {
            UUID droneUuid = UUID.fromString(linkedDroneId);
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(droneUuid);
                if (entity instanceof AddonDroneEntity drone) {
                    return drone;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            // Игрок мог управлять дроном: снимаем якорь вида и форсированную отправку чанков.
            // Region-ticket чанков дрона снимет releaseAllExcept на следующем тике (игрока
            // больше нет в списке → дрон не попадёт в активные).
            ru.liko.wrbdrones.util.PilotViewAnchors.clearAnchor(serverPlayer.getUUID());
            ru.liko.wrbdrones.util.ChunkSendBooster.setBoosted(serverPlayer.getUUID(), false);
        }
    }
}
