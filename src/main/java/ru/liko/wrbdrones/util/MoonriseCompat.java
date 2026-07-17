package ru.liko.wrbdrones.util;

import net.minecraft.server.level.ServerPlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Рефлексивный мост к пер-игроковым view-distance Moonrise. Под Moonrise ванильные
 * пакеты радиуса кэша бесполезны (он шлёт свои из {@code PlayerChunkLoaderData.update()}
 * по значениям {@code ViewDistanceHolder}), поэтому расширение обзора пилота до
 * {@code drone_view_radius} делается через
 * {@code ChunkSystemServerPlayer.moonrise$getViewDistanceHolder()}:
 * {@code setLoadViewDistance(r+1)} + {@code setSendViewDistance(r)}; {@code -1} — сброс
 * к серверному дефолту. Семантика Moonrise: {@code send ≤ load−1}, причём заданный
 * player-send НЕ режется клиентским view-distance — поэтому вызывающий передаёт уже
 * срезанный {@code DroneChunkLoader.viewRadius(player)}.
 *
 * <p>Прямых ссылок на классы Moonrise нет — без мода всё превращается в no-op
 * (паттерн {@code RebBackpackUtils}).</p>
 */
public final class MoonriseCompat {

    private static final MethodHandle GET_HOLDER;
    private static final MethodHandle SET_LOAD;
    private static final MethodHandle SET_SEND;

    static {
        MethodHandle getHolder = null;
        MethodHandle setLoad = null;
        MethodHandle setSend = null;
        try {
            Class<?> playerIface = Class.forName(
                    "ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer");
            Class<?> holderClass = Class.forName(
                    "ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader$ViewDistanceHolder");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getHolder = lookup.findVirtual(playerIface, "moonrise$getViewDistanceHolder",
                    MethodType.methodType(holderClass));
            setLoad = lookup.findVirtual(holderClass, "setLoadViewDistance",
                    MethodType.methodType(void.class, int.class));
            setSend = lookup.findVirtual(holderClass, "setSendViewDistance",
                    MethodType.methodType(void.class, int.class));
        } catch (Throwable ignored) {
            // Moonrise отсутствует или несовместимой версии — мост выключен.
            getHolder = null;
            setLoad = null;
            setSend = null;
        }
        GET_HOLDER = getHolder;
        SET_LOAD = setLoad;
        SET_SEND = setSend;
    }

    private MoonriseCompat() {
    }

    /** {@code true}, когда Moonrise установлен и мост рабочий. */
    public static boolean isLoaded() {
        return GET_HOLDER != null;
    }

    /** Ставит пилоту персональный радиус обзора (в чанках). */
    public static void setPilotViewRadius(final ServerPlayer player, final int radius) {
        apply(player, radius + 1, radius);
    }

    /** Возвращает пилоту серверные дефолты view-distance. */
    public static void resetPilotViewRadius(final ServerPlayer player) {
        apply(player, -1, -1);
    }

    private static void apply(final ServerPlayer player, final int load, final int send) {
        if (GET_HOLDER == null || player == null) {
            return;
        }
        try {
            Object holder = GET_HOLDER.invoke(player);
            SET_LOAD.invoke(holder, load);
            SET_SEND.invoke(holder, send);
        } catch (Throwable ignored) {
            // Дрейф API Moonrise: фича деградирует молча, управление дроном важнее.
        }
    }
}
