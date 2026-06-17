package ru.liko.wrbdrones.util;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный реестр «центров обзора» пилотов: пока игрок управляет дроном без
 * телепорта, его поток чанков/сущностей центрируется не на нём, а на дроне.
 * Миксины видят только {@link net.minecraft.server.level.ServerPlayer}; этот
 * реестр — мост от UUID игрока к сущности дрона, чью позицию надо использовать
 * как центр трекинга. Dist-neutral (без {@code @OnlyIn}).
 */
public final class PilotViewAnchors {

    private static final Map<UUID, Entity> ANCHORS = new ConcurrentHashMap<>();

    private PilotViewAnchors() {}

    public static void setAnchor(final UUID playerId, final Entity drone) {
        if (playerId == null || drone == null) return;
        ANCHORS.put(playerId, drone);
    }

    public static void clearAnchor(final UUID playerId) {
        if (playerId == null) return;
        ANCHORS.remove(playerId);
    }

    @Nullable
    public static Entity getAnchorDrone(final UUID playerId) {
        if (ANCHORS.isEmpty()) return null;
        Entity drone = ANCHORS.get(playerId);
        if (drone != null && drone.isRemoved()) {
            ANCHORS.remove(playerId);
            return null;
        }
        return drone;
    }

    /** Дешёвая проверка пустоты — горячий путь миксина ChunkMap. */
    public static boolean isEmpty() {
        return ANCHORS.isEmpty();
    }
}
