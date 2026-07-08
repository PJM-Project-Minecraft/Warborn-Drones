package ru.liko.wrbdrones.entity.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Terrain-following (облёт рельефа) — stateless-хелпер. Сэмплирует высоту
 * рельефа под снарядом и с упреждением по курсу, берёт максимум (чтобы не
 * врезаться в поднимающийся склон), возвращает «пол» — Y поверхности. Вызывающий
 * прибавляет зазор (clearance) и сглаживает (low-pass), чтобы стыки биомов не
 * дёргали тангаж.
 *
 * <p>Использует {@link Heightmap.Types#MOTION_BLOCKING}: верхний непроходимый
 * блок (включая жидкости). На не прогруженных чанках getHeight может вернуть 0 —
 * поэтому Shahed принудительно грузит чанки вокруг себя (handleChunkLoading).
 */
public final class TerrainFollower {

    private TerrainFollower() {
    }

    /**
     * Высота поверхности рельефа с учётом упреждения по курсу.
     *
     * @param level     мир
     * @param pos       текущая позиция снаряда
     * @param heading   направление полёта (нормализация внутри); может быть нулём
     * @param lookahead упреждение вперёд (бл); 0 = только точка под снарядом
     * @return Y поверхности (максимум из «под» и «впереди»)
     */
    public static double sampleGroundY(Level level, Vec3 pos, Vec3 heading, double lookahead) {
        int x = Mth.floor(pos.x);
        int z = Mth.floor(pos.z);
        int groundNow = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (lookahead <= 0.0) {
            return groundNow;
        }
        double len = heading.lengthSqr();
        if (len < 1.0e-6) {
            return groundNow;
        }
        Vec3 ahead = pos.add(heading.scale(lookahead / Math.sqrt(len)));
        int groundAhead = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                Mth.floor(ahead.x), Mth.floor(ahead.z));
        return Math.max(groundNow, groundAhead);
    }
}
