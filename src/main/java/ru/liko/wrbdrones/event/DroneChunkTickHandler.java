package ru.liko.wrbdrones.event;

import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.misc.MonitorItem;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
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
import ru.liko.wrbdrones.util.SignalCalculator;

import java.util.UUID;

/**
 * Обработчик тика сервера для загрузки чанков дронов.
 * Проверяет мониторы у игроков и загружает чанки связанных дронов,
 * чтобы дроны могли тикать даже когда находятся далеко от игрока.
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

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            AddonDroneEntity drone = checkPlayerMonitor(player);
            if (checkSignal && drone != null) {
                checkServerSignalCutoff(player, drone);
            }
        }
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

    private static AddonDroneEntity checkPlayerMonitor(ServerPlayer player) {
        // Проверяем монитор в главной руке
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

        // Принудительно загружаем чанк дрона по сохранённой позиции
        if (tag.contains("PosX") && tag.contains("PosZ")) {
            double posX = tag.getDouble("PosX");
            double posZ = tag.getDouble("PosZ");

            ServerLevel level = player.serverLevel();
            int chunkX = (int) Math.floor(posX) >> 4;
            int chunkZ = (int) Math.floor(posZ) >> 4;

            // Принудительно загружаем чанк (это синхронная операция)
            level.getChunk(chunkX, chunkZ);

            // Также загружаем соседние чанки для надёжности
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.getChunk(chunkX + dx, chunkZ + dz);
                }
            }

            // Добавляем тикет чтобы чанки оставались загруженными
            try {
                // ChunkLoadManager.ensureChunksLoaded(level, entityId, new ChunkPos(chunkX,
                // chunkZ));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Теперь ищем дрон - чанки уже загружены
        try {
            UUID droneUuid = UUID.fromString(linkedDroneId);

            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(droneUuid);
                if (entity instanceof AddonDroneEntity drone) {
                    // ChunkLoadManager.ensureChunksLoaded(level, drone.getId(),
                    // drone.chunkPosition());
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
            // Если игрок управлял дроном, очищаем всё
            ru.liko.wrbdrones.util.PlayerDecoyManager.removeDecoy(serverPlayer.getUUID());
            // Страховка: снимаем форсированную отправку чанков, если игрок вышел в полёте
            ru.liko.wrbdrones.util.ChunkSendBooster.setBoosted(serverPlayer.getUUID(), false);
        }
    }
}
