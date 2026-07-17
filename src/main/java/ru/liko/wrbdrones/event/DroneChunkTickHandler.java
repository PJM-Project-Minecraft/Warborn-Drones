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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Каждый серверный тик держит FULL-чанки обзора дронов, которыми сейчас управляют
 * через активный монитор, чтобы мир стримился пилоту вдали от его тела.
 *
 * <p>Загрузку делает {@link DroneChunkLoader} отдельными FULL-only tickets — НЕ
 * трогая player-ticket и учёт игроков в {@code DistanceManager}. Каждый тик собираем
 * множество активных дронов и снимаем области обзора у остальных
 * ({@link DroneChunkLoader#releaseAllExcept}). Центральный ENTITY_TICKING-ticket
 * связанного дрона обслуживает сама сущность.</p>
 */
@EventBusSubscriber(modid = Wrbdrones.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DroneChunkTickHandler {

    private static int signalCheckTickCounter = 0;

    private record ActiveDrone(AddonDroneEntity drone, int viewDistance) {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        signalCheckTickCounter++;
        boolean checkSignal = ServerConfig.SIGNAL_SERVER_CUTOFF_ENABLED.get()
                && signalCheckTickCounter >= ServerConfig.SIGNAL_SERVER_CHECK_INTERVAL_TICKS.get();
        if (checkSignal) signalCheckTickCounter = 0;

        int serverViewDistance = event.getServer().getPlayerList().getViewDistance();

        Map<UUID, ActiveDrone> activeDrones = new HashMap<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            AddonDroneEntity monitorDrone = findMonitorDrone(player);
            Entity anchorEntity = ru.liko.wrbdrones.util.PilotViewAnchors.getAnchorDrone(player.getUUID());

            // Сторож застрявшего якоря. Штатно якорь снимает baseTick дрона, увидев
            // Using=false — но замёрзший/выгруженный дрон не тикает, и якорь навечно
            // держит поток чанков пилота на дроне: игрок ходит по белому невыгруженному
            // миру. Серверный тик не зависит от тика дрона, поэтому снимаем здесь.
            if (anchorEntity instanceof AddonDroneEntity anchorDrone && anchorDrone != monitorDrone) {
                anchorDrone.endRemoteControl(player);
                // Страховка: endRemoteControl при уже мёртвой сессии выходит рано,
                // не трогая якорь. Снятие идемпотентно.
                ru.liko.wrbdrones.util.PilotViewAnchors.clearAnchor(player.getUUID());
                anchorEntity = null;
            }

            AddonDroneEntity drone =
                    anchorEntity instanceof AddonDroneEntity anchored ? anchored : monitorDrone;
            if (drone == null) {
                continue;
            }
            // Радиус tickets = радиус трекинга: ванильный clamp, расширенный drone_view_radius
            // (см. ChunkMapPilotAnchorMixin) — иначе кромка трекинга останется без чанков.
            int viewDistance = Math.max(
                    Math.max(2, Math.min(serverViewDistance, player.requestedViewDistance())),
                    DroneChunkLoader.viewRadius(player));
            activeDrones.merge(
                    drone.getUUID(),
                    new ActiveDrone(drone, viewDistance),
                    (left, right) -> left.viewDistance >= right.viewDistance ? left : right);
            if (checkSignal) {
                checkServerSignalCutoff(player, drone);
            }
        }
        for (ActiveDrone active : activeDrones.values()) {
            // Тикание дрона не должно зависеть от его собственного тика (baseTick ставит
            // тикет ДО движения — замёрзший дрон сам себя уже не разбудит). Пока пилот
            // держит управление, серверный тик продлевает тикет за него: это же
            // «размораживает» дрон, застрявший в нетикающем чанке, при взятии монитора.
            DroneChunkLoader.keepEntityLoaded(active.drone);
            DroneChunkLoader.keepLoaded(active.drone, active.viewDistance);
        }
        // Дрон, которого в этот тик никто не держит, теряет тикет и выгружается.
        DroneChunkLoader.releaseAllExcept(activeDrones.keySet());
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
     * Возвращает дрон, к которому в главной руке игрока привязан монитор с
     * {@code Using=true}; иначе {@code null}. Условие то же, что в проверке
     * {@code isUsingMonitor} в {@code AddonDroneEntity.baseTick} — сторож якоря выше
     * опирается на их совпадение. Работой с чанками здесь не занимаемся.
     */
    private static AddonDroneEntity findMonitorDrone(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.MONITOR.get())) {
            return null;
        }
        var tag = NBTTool.getTag(mainHand);
        if (!tag.getBoolean(MonitorItem.LINKED) || !tag.getBoolean(MonitorItem.USING)) {
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
            // Ticket тела пилота (ставится в beginRemoteControl, ключ — UUID игрока).
            DroneChunkLoader.releaseEntity(serverPlayer.getUUID());
        }
    }
}
