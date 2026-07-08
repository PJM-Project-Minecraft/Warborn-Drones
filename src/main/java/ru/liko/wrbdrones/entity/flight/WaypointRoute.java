package ru.liko.wrbdrones.entity.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Логика продвижения по маршруту путевых точек (waypoints). Stateless-хелпер:
 * состояние (список + активный индекс) хранит сущность, здесь только чистые
 * функции выбора/смены активной точки.
 *
 * <p>Список {@code route} всегда непустой, последний элемент — финальная цель
 * (подрыв). Промежуточные точки — «пролётные»: при входе в радиус
 * {@code advanceRadius} (по горизонтали) активный индекс увеличивается.
 * Финальную точку не сменяем — на неё работает терминальное наведение.
 */
public final class WaypointRoute {

    private WaypointRoute() {
    }

    /** Финальная цель маршрута (последняя точка). */
    public static Vec3 finalTarget(List<Vec3> route) {
        return route.get(route.size() - 1);
    }

    /** Активная точка маршрута (к ней сейчас летим в крейсерском режиме). */
    public static Vec3 active(List<Vec3> route, int idx) {
        return route.get(Mth.clamp(idx, 0, route.size() - 1));
    }

    /**
     * Продвинуть активный индекс, если снаряд вошёл в радиус смены текущей
     * (не финальной) точки. Возвращает новый индекс.
     */
    public static int advance(List<Vec3> route, int idx, Vec3 pos, double advanceRadius) {
        if (idx >= route.size() - 1) {
            return idx; // на финальной — не сменяем
        }
        Vec3 wp = route.get(idx);
        double dx = wp.x - pos.x;
        double dz = wp.z - pos.z;
        double r2 = advanceRadius * advanceRadius;
        if (dx * dx + dz * dz < r2) {
            return idx + 1;
        }
        return idx;
    }

    /** Истинно ли активная точка — финальная (терминальная цель). */
    public static boolean onFinal(List<Vec3> route, int idx) {
        return idx >= route.size() - 1;
    }
}
