package ru.liko.wrbdrones.util;

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
 * Держит чанки ВОКРУГ дрона загруженными собственным region-ticket'ом, чтобы дрон
 * тикал и стримился пилоту вдали от игроков — НЕ трогая player-ticket и учёт игроков
 * в {@code DistanceManager}.
 *
 * <p>Заменяет прежний подход (подмена {@code SectionPos.of(player)} в {@code ChunkMap}
 * секцией дрона): тот перевешивал player-ticket пилота на дрон и рассинхронизировал
 * глобальный {@code playersPerChunk}/уровни тикетов → при малейшем дисбалансе
 * {@code addPlayer}/{@code removePlayer} падал серверный тик чанков и прогрузка
 * ломалась у ВСЕХ игроков. Region-ticket независим от учёта игроков и повредить общий
 * учёт не может.</p>
 *
 * <p>Тикет ставится с радиусом {@link ServerConfig#DRONE_CHUNK_RADIUS}: центр получает
 * уровень {@code byStatus(FULL)=33 − radius}, т.е. при {@code radius ≥ 2} чанк дрона
 * становится {@code ENTITY_TICKING} и дрон тикает. Драйвится каждый серверный тик из
 * {@code DroneChunkTickHandler}: {@link #keepLoaded} для активных дронов и
 * {@link #releaseAllExcept} для снятия тикетов у дронов, которых больше никто не
 * держит. Dist-neutral (без {@code @OnlyIn}).</p>
 */
public final class DroneChunkLoader {

    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("wrbdrones_drone", Comparator.comparingLong(ChunkPos::toLong));

    /**
     * Запас (в чанках) сверх клиентского view-distance: грузим кольцо на столько чанков
     * дальше, чем видит пилот. Клиент запрашивает view-distance вокруг дрона; лишние
     * кольца сервер не шлёт, но держит СГЕНЕРИРОВАННЫМИ, поэтому чанк, входящий в обзор
     * при движении дрона, уже готов и отправляется без паузы на генерацию — подгрузка
     * идёт плавно, а не рывками на кромке. Симметрично → помогает и при поворотах.
     */
    private static final int LOOKAHEAD = 4;

    private record Held(ServerLevel level, ChunkPos pos, int radius) {}

    private static final Map<UUID, Held> HELD = new ConcurrentHashMap<>();

    private DroneChunkLoader() {}

    /**
     * Гарантирует region-ticket на текущем чанке дрона. Идемпотентно при неизменном
     * чанке; при переходе дрона в новый чанк/измерение снимает старый тикет и ставит
     * новый — тикающий регион следует за дроном. Значением тикета служит сам
     * {@link ChunkPos} (идентичность тикета в {@code SortedArraySet}).
     *
     * @param viewDistance радиус загрузки в чанках — обычно view-distance сервера,
     *                     чтобы пилот грузил вокруг дрона столько же чанков, сколько
     *                     обычный игрок вокруг себя. Ограничивается сверху
     *                     {@link ServerConfig#DRONE_CHUNK_RADIUS} и снизу 2 (иначе
     *                     чанк дрона не станет ENTITY_TICKING и дрон не будет тикать).
     */
    public static void keepLoaded(final Entity drone, final int viewDistance) {
        if (drone == null || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = drone.getUUID();
        ChunkPos pos = drone.chunkPosition();
        // Грузим на LOOKAHEAD чанков дальше клиентского обзора для плавной подгрузки,
        // но не выше настраиваемого потолка.
        int radius = Math.max(2, Math.min(viewDistance + LOOKAHEAD, ServerConfig.DRONE_CHUNK_RADIUS.get()));
        Held cur = HELD.get(id);
        if (cur != null && cur.level == level && cur.pos.equals(pos) && cur.radius == radius) {
            return;
        }
        if (cur != null) {
            cur.level.getChunkSource().removeRegionTicket(TICKET, cur.pos, cur.radius, cur.pos);
        }
        level.getChunkSource().addRegionTicket(TICKET, pos, radius, pos);
        HELD.put(id, new Held(level, pos, radius));
    }

    /** Снимает тикет конкретного дрона (удаление/взрыв/отвязка). Идемпотентно. */
    public static void release(final UUID droneId) {
        if (droneId == null) return;
        Held h = HELD.remove(droneId);
        if (h != null) {
            h.level.getChunkSource().removeRegionTicket(TICKET, h.pos, h.radius, h.pos);
        }
    }

    /**
     * Снимает тикеты у всех дронов, чьих UUID нет в {@code keep}. Вызывается раз в
     * серверный тик после обхода игроков: дрон, которого в этот тик никто не держит
     * (монитор убран, игрок вышел, дрон удалён), теряет тикет — его чанк выгружается.
     */
    public static void releaseAllExcept(final Set<UUID> keep) {
        Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Held> e = it.next();
            if (keep.contains(e.getKey())) {
                continue;
            }
            Held h = e.getValue();
            h.level.getChunkSource().removeRegionTicket(TICKET, h.pos, h.radius, h.pos);
            it.remove();
        }
    }
}
