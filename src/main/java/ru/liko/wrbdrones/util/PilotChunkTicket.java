package ru.liko.wrbdrones.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Держит домашний чанк пилота загруженным, пока тот управляет дроном без телепорта.
 * Без этого собственный player-ticket игрока «уезжает» вместе с центром трекинга на
 * дрон, и тело пилота во выгруженном чанке перестаёт тикать/быть уязвимым.
 */
public final class PilotChunkTicket {

    // playerId -> level, чтобы корректно снять именно тот чанк, что держали.
    private static final Map<UUID, ServerLevel> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkPos> CHUNKS = new ConcurrentHashMap<>();

    private PilotChunkTicket() {}

    public static void hold(final ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        ChunkPos pos = new ChunkPos(player.blockPosition());
        level.setChunkForced(pos.x, pos.z, true);
        LEVELS.put(player.getUUID(), level);
        CHUNKS.put(player.getUUID(), pos);
    }

    public static void release(final ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        ServerLevel level = LEVELS.remove(id);
        ChunkPos pos = CHUNKS.remove(id);
        if (level != null && pos != null) {
            level.setChunkForced(pos.x, pos.z, false);
        }
    }
}
