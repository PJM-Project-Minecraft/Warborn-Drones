package ru.liko.wrbdrones.util;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import ru.liko.wrbdrones.config.ServerConfig;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Держит дрон тикающим и отдельно подготавливает FULL-чанки его клиентского обзора.
 *
 * <p>Для центра используется region-ticket радиуса {@value #ENTITY_TICKET_RADIUS}:
 * чанк дрона и кольцо 3x3 получают {@code ENTITY_TICKING} (см. комментарий у
 * константы), дальше — {@code BLOCK_TICKING} и {@code FULL}. Чанки обзора получают
 * независимые tickets радиуса 0, то есть остаются {@code FULL} и не тикают
 * сущности/блоки.</p>
 *
 * <p>Ключ каждого ticket — UUID дрона. Поэтому два дрона в одном чанке не делят
 * один ticket и снятие загрузки у одного не выгружает второго. При движении набор
 * FULL-чанков обновляется диффом: неизменившаяся часть области не снимается и не
 * ставится повторно.</p>
 */
public final class DroneChunkLoader {

    /**
     * Радиус 3: ENTITY_TICKING получают центр И кольцо 3x3 (уровень 31), BLOCK_TICKING —
     * кольцо 5x5, FULL — 7x7. С радиусом 2 тикал только центральный чанк, и вдали от
     * игроков дрон замерзал навсегда на первой же границе чанка: тикет ставится из
     * baseTick ДО движения, дрон уходил в BLOCK_TICKING-кольцо, переставал тикать и уже
     * никогда не переносил тикет за собой. С тикающим кольцом пересечение границы не
     * прерывает тик, и тикет успевает переехать.
     */
    private static final int ENTITY_TICKET_RADIUS = 3;

    private static final TicketType<UUID> ENTITY_TICKET =
            TicketType.create("wrbdrones_drone_entity", Comparator.<UUID>naturalOrder());
    private static final TicketType<UUID> VIEW_TICKET =
            TicketType.create("wrbdrones_drone_view", Comparator.<UUID>naturalOrder());

    private record CenterHeld(ServerLevel level, ChunkPos pos) {}

    private record ViewHeld(ServerLevel level, ChunkPos center, int radius, LongSet chunks) {}

    private static final Map<UUID, CenterHeld> CENTERS = new ConcurrentHashMap<>();
    private static final Map<UUID, ViewHeld> VIEWS = new ConcurrentHashMap<>();

    private DroneChunkLoader() {}

    /**
     * Радиус обзора дрона для этого пилота: конфиг {@code drone_view_radius}, срезанный
     * запрошенным клиентским view-distance (чанки дальше клиент всё равно не отрисует).
     * Серверный view-distance намеренно НЕ участвует — в этом смысл фичи.
     */
    public static int viewRadius(final net.minecraft.server.level.ServerPlayer player) {
        return Math.max(2, Math.min(player.requestedViewDistance(), ServerConfig.DRONE_VIEW_RADIUS.get()));
    }

    /**
     * Держит тикающим только центральный чанк связанного дрона. Вызывается из
     * серверного тика сущности, поэтому ticket следует за дроном по границам чанков.
     */
    public static void keepEntityLoaded(final Entity drone) {
        if (drone == null || !(drone.level() instanceof ServerLevel level)) {
            return;
        }

        UUID id = drone.getUUID();
        ChunkPos pos = drone.chunkPosition();
        CenterHeld current = CENTERS.get(id);
        if (current != null && current.level == level && current.pos.equals(pos)) {
            return;
        }

        // Сначала ставим новый ticket, затем снимаем старый: между операциями дрон
        // не остаётся без тикающего чанка.
        level.getChunkSource().addRegionTicket(ENTITY_TICKET, pos, ENTITY_TICKET_RADIUS, id);
        if (current != null) {
            current.level.getChunkSource().removeRegionTicket(
                    ENTITY_TICKET, current.pos, ENTITY_TICKET_RADIUS, id);
        }
        CENTERS.put(id, new CenterHeld(level, pos));
    }

    /** Снимает только центральный ENTITY_TICKING-ticket дрона. */
    public static void releaseEntity(final UUID droneId) {
        if (droneId == null) {
            return;
        }
        CenterHeld held = CENTERS.remove(droneId);
        if (held != null) {
            held.level.getChunkSource().removeRegionTicket(
                    ENTITY_TICKET, held.pos, ENTITY_TICKET_RADIUS, droneId);
        }
    }

    /**
     * Подготавливает FULL-чанки, которые реально входят в ванильный tracking view
     * пилота. Радиус учитывает клиентский view-distance и серверный safety cap.
     */
    public static void keepLoaded(final Entity drone, final int viewDistance) {
        if (drone == null || !(drone.level() instanceof ServerLevel level)) {
            return;
        }

        UUID id = drone.getUUID();
        ChunkPos center = drone.chunkPosition();
        int radius = Math.max(2, Math.min(viewDistance, ServerConfig.DRONE_CHUNK_RADIUS.get()));
        ViewHeld current = VIEWS.get(id);
        if (current != null
                && current.level == level
                && current.center.equals(center)
                && current.radius == radius) {
            return;
        }

        LongSet desired = collectViewChunks(center, radius);

        // Добавляем новую кромку до снятия старой, чтобы движение не создавало окно
        // с незагруженным чанком перед камерой.
        for (long packed : desired) {
            if (current == null || current.level != level || !current.chunks.contains(packed)) {
                ChunkPos pos = new ChunkPos(packed);
                level.getChunkSource().addRegionTicket(VIEW_TICKET, pos, 0, id);
            }
        }

        if (current != null) {
            for (long packed : current.chunks) {
                if (current.level != level || !desired.contains(packed)) {
                    ChunkPos pos = new ChunkPos(packed);
                    current.level.getChunkSource().removeRegionTicket(VIEW_TICKET, pos, 0, id);
                }
            }
        }

        VIEWS.put(id, new ViewHeld(level, center, radius, desired));
    }

    private static LongSet collectViewChunks(final ChunkPos center, final int radius) {
        LongSet chunks = new LongOpenHashSet();
        ChunkTrackingView.of(center, radius).forEach(pos -> chunks.add(pos.toLong()));
        return chunks;
    }

    /** Снимает FULL-only область обзора конкретного дрона. */
    private static void releaseView(final UUID droneId) {
        ViewHeld held = VIEWS.remove(droneId);
        if (held == null) {
            return;
        }
        for (long packed : held.chunks) {
            held.level.getChunkSource().removeRegionTicket(
                    VIEW_TICKET, new ChunkPos(packed), 0, droneId);
        }
    }

    /** Снимает все tickets конкретного дрона. Идемпотентно. */
    public static void release(final UUID droneId) {
        if (droneId == null) {
            return;
        }
        releaseView(droneId);
        releaseEntity(droneId);
    }

    /**
     * Снимает области обзора у дронов, которых в этом тике никто не просматривает.
     * Центральные tickets здесь не затрагиваются: связанный дрон может продолжать
     * тикать без открытого FPV-вида.
     */
    public static void releaseAllExcept(final Set<UUID> keep) {
        Iterator<UUID> iterator = VIEWS.keySet().iterator();
        while (iterator.hasNext()) {
            UUID droneId = iterator.next();
            if (keep.contains(droneId)) {
                continue;
            }
            ViewHeld held = VIEWS.get(droneId);
            if (held != null && VIEWS.remove(droneId, held)) {
                for (long packed : held.chunks) {
                    held.level.getChunkSource().removeRegionTicket(
                            VIEW_TICKET, new ChunkPos(packed), 0, droneId);
                }
            }
        }
    }
}
