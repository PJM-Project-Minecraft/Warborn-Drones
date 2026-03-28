package ru.liko.wrbdrones.util;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер загрузки чанков для дронов.
 * 
 * Использует систему тикетов Minecraft для правильной загрузки чанков.
 * Улучшенная версия с поддержкой:
 * - Форсированной загрузки чанков (entity ticking)
 * - Отслеживания по UUID дрона
 * - Автоматического обновления позиции тикета при движении дрона
 */
public final class ChunkLoadManager {
    
    private ChunkLoadManager() {}
    
    private static final TicketType<Integer> DRONE_TICKET = TicketType.create("wrbdrones_drone", Integer::compareTo, 40);
    private static final TicketType<UUID> DRONE_UUID_TICKET = TicketType.create("wrbdrones_drone_uuid", UUID::compareTo, 40);
    
    private static final int DEFAULT_CHUNK_RADIUS = 3;
    private static final int ENTITY_TICKING_LEVEL = 31;
    
    private static final Map<Integer, TicketData> activeTickets = new ConcurrentHashMap<>();
    private static final Map<UUID, UUIDTicketData> uuidTickets = new ConcurrentHashMap<>();
    
    private static class TicketData {
        final ServerLevel level;
        ChunkPos pos;
        final int radius;
        long lastUpdate;
        
        TicketData(ServerLevel level, ChunkPos pos, int radius) {
            this.level = level;
            this.pos = pos;
            this.radius = radius;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
    
    private static class UUIDTicketData {
        final ServerLevel level;
        ChunkPos pos;
        final int radius;
        
        UUIDTicketData(ServerLevel level, ChunkPos pos, int radius) {
            this.level = level;
            this.pos = pos;
            this.radius = radius;
        }
    }
    
    /**
     * Обеспечивает загрузку чанков вокруг позиции дрона.
     * Чанки будут тикать (entity ticking level).
     */
    public static void ensureChunksLoaded(ServerLevel level, int entityId, ChunkPos pos) {
        ensureChunksLoaded(level, entityId, pos, DEFAULT_CHUNK_RADIUS);
    }
    
    /**
     * Обеспечивает загрузку чанков с указанным радиусом.
     */
    public static void ensureChunksLoaded(ServerLevel level, int entityId, ChunkPos pos, int radius) {
        if (level == null || pos == null) return;
        
        TicketData existing = activeTickets.get(entityId);
        
        if (existing != null) {
            if (existing.pos.equals(pos) && existing.radius == radius && existing.level == level) {
                existing.lastUpdate = System.currentTimeMillis();
                return;
            }
            removeTicket(existing.level, existing.pos, existing.radius, entityId);
        }
        
        ServerChunkCache chunkSource = level.getChunkSource();
        chunkSource.addRegionTicket(DRONE_TICKET, pos, radius, entityId);
        
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos neighborPos = new ChunkPos(pos.x + dx, pos.z + dz);
                level.getChunk(neighborPos.x, neighborPos.z);
            }
        }
        
        activeTickets.put(entityId, new TicketData(level, pos, radius));
    }
    
    /**
     * Загружает чанки по UUID дрона (для отслеживания через Monitor).
     */
    public static void ensureChunksLoadedByUUID(ServerLevel level, UUID droneUUID, ChunkPos pos) {
        ensureChunksLoadedByUUID(level, droneUUID, pos, DEFAULT_CHUNK_RADIUS);
    }
    
    /**
     * Загружает чанки по UUID с указанным радиусом.
     */
    public static void ensureChunksLoadedByUUID(ServerLevel level, UUID droneUUID, ChunkPos pos, int radius) {
        if (level == null || pos == null || droneUUID == null) return;
        
        UUIDTicketData existing = uuidTickets.get(droneUUID);
        
        if (existing != null) {
            if (existing.pos.equals(pos) && existing.radius == radius && existing.level == level) {
                return;
            }
            removeUUIDTicket(existing.level, existing.pos, existing.radius, droneUUID);
        }
        
        ServerChunkCache chunkSource = level.getChunkSource();
        chunkSource.addRegionTicket(DRONE_UUID_TICKET, pos, radius, droneUUID);
        
        uuidTickets.put(droneUUID, new UUIDTicketData(level, pos, radius));
    }
    
    /**
     * Освобождает тикет загрузки чанков для entity ID.
     */
    public static void releaseChunks(ServerLevel level, int entityId) {
        TicketData existing = activeTickets.remove(entityId);
        if (existing != null) {
            removeTicket(existing.level, existing.pos, existing.radius, entityId);
        }
    }
    
    /**
     * Освобождает тикет загрузки чанков для UUID дрона.
     */
    public static void releaseChunksByUUID(UUID droneUUID) {
        if (droneUUID == null) return;
        UUIDTicketData existing = uuidTickets.remove(droneUUID);
        if (existing != null) {
            removeUUIDTicket(existing.level, existing.pos, existing.radius, droneUUID);
        }
    }
    
    private static void removeTicket(ServerLevel level, ChunkPos pos, int radius, int entityId) {
        if (level == null || pos == null) return;
        try {
            level.getChunkSource().removeRegionTicket(DRONE_TICKET, pos, radius, entityId);
        } catch (Exception ignored) {
        }
    }
    
    private static void removeUUIDTicket(ServerLevel level, ChunkPos pos, int radius, UUID droneUUID) {
        if (level == null || pos == null) return;
        try {
            level.getChunkSource().removeRegionTicket(DRONE_UUID_TICKET, pos, radius, droneUUID);
        } catch (Exception ignored) {
        }
    }
    
    /**
     * Форсированно загружает чанк и ожидает его готовности.
     */
    public static void forceLoadChunk(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null) return;
        level.getChunk(pos.x, pos.z);
    }
    
    /**
     * Проверяет, загружен ли чанк.
     */
    public static boolean isChunkLoaded(ServerLevel level, ChunkPos pos) {
        if (level == null || pos == null) return false;
        return level.getChunkSource().hasChunk(pos.x, pos.z);
    }
    
    /**
     * Очищает все тикеты для уровня.
     */
    public static void clearLevel(ServerLevel level) {
        activeTickets.entrySet().removeIf(entry -> {
            if (entry.getValue().level == level) {
                removeTicket(level, entry.getValue().pos, entry.getValue().radius, entry.getKey());
                return true;
            }
            return false;
        });
        
        uuidTickets.entrySet().removeIf(entry -> {
            if (entry.getValue().level == level) {
                removeUUIDTicket(level, entry.getValue().pos, entry.getValue().radius, entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * Очищает все тикеты (при остановке сервера).
     */
    public static void clearAll() {
        for (var entry : activeTickets.entrySet()) {
            try {
                removeTicket(entry.getValue().level, entry.getValue().pos, entry.getValue().radius, entry.getKey());
            } catch (Exception ignored) {}
        }
        activeTickets.clear();
        
        for (var entry : uuidTickets.entrySet()) {
            try {
                removeUUIDTicket(entry.getValue().level, entry.getValue().pos, entry.getValue().radius, entry.getKey());
            } catch (Exception ignored) {}
        }
        uuidTickets.clear();
    }
    
    /**
     * Очищает устаревшие тикеты (не обновлявшиеся более 5 секунд).
     */
    public static void cleanupStaleTickets() {
        long now = System.currentTimeMillis();
        long staleThreshold = 5000;
        
        activeTickets.entrySet().removeIf(entry -> {
            if (now - entry.getValue().lastUpdate > staleThreshold) {
                removeTicket(entry.getValue().level, entry.getValue().pos, entry.getValue().radius, entry.getKey());
                return true;
            }
            return false;
        });
    }
}
