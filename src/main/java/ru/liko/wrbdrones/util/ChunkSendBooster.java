package ru.liko.wrbdrones.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ru.liko.wrbdrones.config.ServerConfig;

/**
 * Реестр игроков, которым нужно форсировать ускоренную отправку чанков, пока они
 * пилотируют дрон через монитор.
 *
 * <p>Узкое место рендера у быстрых дронов (FPV, Lancet) — ванильный rate-limited
 * {@code PlayerChunkSender}: его {@code desiredChunksPerTick} задаётся клиентом
 * (через {@code ServerboundChunkBatchReceivedPacket}) и не успевает за дроном,
 * поэтому пилот влетает в ещё не отправленные (пустые) чанки. Пока UUID игрока в
 * этом наборе, {@code PlayerChunkSenderMixin} перед каждым батчем поднимает скорость
 * отправки до значений из конфига.</p>
 *
 * <p>dist-neutral (без {@code @OnlyIn}) — обращаются и серверная логика дрона, и
 * миксин общего сайда.</p>
 */
public final class ChunkSendBooster {

    private static final Set<UUID> BOOSTED = ConcurrentHashMap.newKeySet();

    private ChunkSendBooster() {
    }

    /** Помечает/снимает пометку «ускорять отправку чанков» для игрока. */
    public static void setBoosted(final UUID playerId, final boolean boosted) {
        if (playerId == null) {
            return;
        }
        if (boosted) {
            BOOSTED.add(playerId);
        } else {
            BOOSTED.remove(playerId);
        }
    }

    /**
     * Нужно ли сейчас ускорять отправку чанков этому игроку. Сначала дешёвая проверка
     * пустоты набора (вызывается из горячего цикла отправки чанков для каждого игрока
     * каждый тик), и только при наличии пилотов читается конфиг.
     */
    public static boolean isBoosted(final UUID playerId) {
        return !BOOSTED.isEmpty()
                && ServerConfig.CHUNK_SEND_BOOST_ENABLED.get()
                && BOOSTED.contains(playerId);
    }

    /** Форсируемая скорость отправки (chunks/tick). */
    public static float desiredChunksPerTick() {
        return ServerConfig.CHUNK_SEND_BOOST_RATE.get().floatValue();
    }

    /** Форсируемый лимит неподтверждённых батчей в полёте. */
    public static int maxUnacknowledgedBatches() {
        return ServerConfig.CHUNK_SEND_BOOST_BATCHES.get();
    }

    /**
     * Нижний порог скорости отправки чанков для ВСЕХ игроков (chunks/tick); {@code 0} — выключен.
     *
     * <p>Причина та же, что у boost, но без дрона. {@code ChunkBatchSizeCalculator} на клиенте
     * считает {@code 7мс / время_обработки_чанка}, где время меряется между пакетами batch-start
     * и batch-finish в главном потоке — в него попадают кадры отрисовки. Просевший FPS ⇒ клиент
     * просит меньше ⇒ {@code PlayerChunkSender} послушно шлёт по капле (наблюдалось 0.35 чанка/тик
     * при ванильном старте 9.0) ⇒ игрок влетает в пустые чанки. Само не чинится: усреднение
     * инерционное (вес старых сэмплов растёт до 49), помогает только реконнект — он пересоздаёт
     * и калькулятор, и отправитель. Порог удерживает нижнюю границу, что бы клиент ни просил.</p>
     */
    public static float floorChunksPerTick() {
        return ServerConfig.CHUNK_SEND_FLOOR_RATE.get().floatValue();
    }
}
