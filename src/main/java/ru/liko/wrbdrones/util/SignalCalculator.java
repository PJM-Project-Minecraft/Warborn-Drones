package ru.liko.wrbdrones.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Единая точка расчёта качества сигнала между оператором и дроном.
 * Учитывает дистанцию, разность высот, толщину преград (LOS) и помехи РЭБ.
 * Кэширует результат на уровень дрона по {@link Entity#getUUID()} с TTL из конфига.
 *
 * Класс дист-нейтрален — может быть вызван и с клиента (HUD-рендер), и с сервера
 * (авторитарная проверка в {@code DroneChunkTickHandler}).
 */
public final class SignalCalculator {

    public static final TagKey<Block> SOFT_OBSTACLES = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(Wrbdrones.MODID, "soft_obstacles"));

    /**
     * @param finalQuality итоговое качество сигнала, 0..1.
     * @param distanceFactor вклад дистанции, 0..1.
     * @param altitudeFactor вклад высоты (множитель), 0..1.
     * @param wallAttenuation сколько съели стены, 0..1 (1 = всё съели).
     * @param rebFactor вклад глушения РЭБ, 0..1 (1 = полное глушение).
     */
    public record SignalResult(
            double finalQuality,
            double distanceFactor,
            double altitudeFactor,
            double wallAttenuation,
            double rebFactor) {
    }

    private static final class Cached {
        final SignalResult result;
        final long worldTimeStamp;

        Cached(SignalResult result, long worldTimeStamp) {
            this.result = result;
            this.worldTimeStamp = worldTimeStamp;
        }
    }

    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();

    private SignalCalculator() {
    }

    /**
     * Считает качество сигнала с учётом кэша. Кэш сбрасывается, если {@link Level#getGameTime()}
     * ушёл вперёд больше чем на {@code SIGNAL_CACHE_TTL_TICKS}.
     *
     * @param level уровень дрона.
     * @param operatorPos позиция оператора (точка приёма антенны).
     * @param drone сам дрон.
     * @param maxDistance максимальная дистанция связи (после неё дистанционный фактор = 0).
     * @param signalLossDistance дистанция начала падения (для Mavic) или {@code -1} для FPV-кривой
     *                           «спадает с самого начала».
     */
    public static SignalResult compute(Level level, Vec3 operatorPos, Entity drone,
            double maxDistance, double signalLossDistance) {
        if (level == null || operatorPos == null || drone == null) {
            return new SignalResult(0.0, 0.0, 1.0, 0.0, 0.0);
        }

        long now = level.getGameTime();
        int ttl = ServerConfig.SIGNAL_CACHE_TTL_TICKS.get();
        UUID id = drone.getUUID();
        Cached cached = CACHE.get(id);
        if (cached != null && now - cached.worldTimeStamp <= ttl) {
            return cached.result;
        }

        SignalResult fresh = computeUncached(level, operatorPos, drone, maxDistance, signalLossDistance);
        CACHE.put(id, new Cached(fresh, now));
        return fresh;
    }

    /**
     * Версия без кэша — для редких авторитарных проверок на сервере, где нужно
     * самое свежее значение.
     */
    public static SignalResult computeUncached(Level level, Vec3 operatorPos, Entity drone,
            double maxDistance, double signalLossDistance) {
        Vec3 dronePos = drone.position();
        double distance = operatorPos.distanceTo(dronePos);

        double distanceFactor = computeDistanceFactor(distance, maxDistance, signalLossDistance);
        double altitudeFactor = ServerConfig.SIGNAL_ALTITUDE_ENABLED.get()
                ? computeAltitudeFactor(operatorPos, dronePos)
                : 1.0;

        double wallAttenuation = ServerConfig.SIGNAL_LOS_ENABLED.get()
                ? computeWallAttenuation(level, operatorPos, drone.getEyePosition())
                : 0.0;

        double rebFactor = RebUtils.getRebFactor(drone);

        double quality = distanceFactor
                * altitudeFactor
                * (1.0 - wallAttenuation)
                * (1.0 - rebFactor);
        quality = Math.max(0.0, Math.min(1.0, quality));

        return new SignalResult(quality, distanceFactor, altitudeFactor, wallAttenuation, rebFactor);
    }

    private static double computeDistanceFactor(double distance, double maxDistance, double signalLossDistance) {
        if (maxDistance <= 0.0) return 0.0;

        if (signalLossDistance > 0.0 && signalLossDistance < maxDistance) {
            // Mavic-ветка: до signalLossDistance сигнал полный, потом квадратичный спад.
            if (distance <= signalLossDistance) return 1.0;
            double norm = Math.min(1.0, (distance - signalLossDistance) / (maxDistance - signalLossDistance));
            double f = 1.0 - norm;
            return f * f;
        }

        // FPV-ветка: спадает с нуля, как было раньше.
        double f = Math.max(0.0, 1.0 - (distance / maxDistance));
        return f * f;
    }

    private static double computeAltitudeFactor(Vec3 operatorPos, Vec3 dronePos) {
        double dy = dronePos.y - operatorPos.y;
        double floor = ServerConfig.ALT_PENALTY_FLOOR.get();
        double ceil = ServerConfig.ALT_BONUS_CEIL.get();
        double minMul = ServerConfig.ALT_MIN_MULTIPLIER.get();

        if (dy >= ceil) return 1.0;
        if (dy <= floor) return minMul;

        double range = ceil - floor;
        if (range <= 0.0) return 1.0;
        double t = (dy - floor) / range;
        return minMul + t * (1.0 - minMul);
    }

    /**
     * Voxel-traversal от {@code from} до {@code to}: суммирует штраф за каждую клетку,
     * через которую проходит луч. Шаг — 1 блок (Amanatides–Woo). Лимит длины обхода
     * берётся из {@code WALL_MAX_RAY_BLOCKS}, итог клампится на {@code WALL_MAX_TOTAL_ATTENUATION}.
     */
    private static double computeWallAttenuation(Level level, Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double rayLen = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (rayLen < 1.0e-6) return 0.0;

        double hardPer = ServerConfig.WALL_HARD_PER_BLOCK.get();
        double softPer = ServerConfig.WALL_SOFT_PER_BLOCK.get();
        double cap = ServerConfig.WALL_MAX_TOTAL_ATTENUATION.get();
        int maxSteps = ServerConfig.WALL_MAX_RAY_BLOCKS.get();

        // Если оба коэффициента нули — нет смысла обходить.
        if (hardPer <= 0.0 && softPer <= 0.0) return 0.0;

        int x = (int) Math.floor(from.x);
        int y = (int) Math.floor(from.y);
        int z = (int) Math.floor(from.z);

        int endX = (int) Math.floor(to.x);
        int endY = (int) Math.floor(to.y);
        int endZ = (int) Math.floor(to.z);

        int stepX = Integer.signum((int) Math.signum(dx));
        int stepY = Integer.signum((int) Math.signum(dy));
        int stepZ = Integer.signum((int) Math.signum(dz));

        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;

        double tMaxX = stepX > 0 ? ((x + 1) - from.x) / dx
                : (stepX < 0 ? (from.x - x) / -dx : Double.POSITIVE_INFINITY);
        double tMaxY = stepY > 0 ? ((y + 1) - from.y) / dy
                : (stepY < 0 ? (from.y - y) / -dy : Double.POSITIVE_INFINITY);
        double tMaxZ = stepZ > 0 ? ((z + 1) - from.z) / dz
                : (stepZ < 0 ? (from.z - z) / -dz : Double.POSITIVE_INFINITY);

        double total = 0.0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < maxSteps; i++) {
            pos.set(x, y, z);

            // Чанк может быть не загружен на клиенте/сервере — пропускаем такую клетку
            // (трактуем как воздух, чтобы не штрафовать игрока за стриминг чанков).
            if (level.hasChunkAt(pos)) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    double penalty;
                    if (state.is(SOFT_OBSTACLES)) {
                        penalty = softPer;
                    } else if (state.canOcclude()) {
                        penalty = hardPer;
                    } else {
                        // Не-occluding не-soft (заборы, кнопки, ковры) — игнорируем.
                        penalty = 0.0;
                    }
                    if (penalty > 0.0) {
                        total += penalty;
                        if (total >= cap) return cap;
                    }
                }
            }

            if (x == endX && y == endY && z == endZ) break;

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return Math.min(cap, total);
    }

    /** Сбросить кэш — для случаев, когда конфиг перечитан или дрон удалён. */
    public static void invalidate(UUID droneId) {
        CACHE.remove(droneId);
    }

    /** Полный сброс кэша (например, при перезагрузке мира). */
    public static void invalidateAll() {
        CACHE.clear();
    }
}
