package ru.liko.wrbdrones.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.RebEntity;
import ru.liko.wrbdrones.entity.RebMiniEntity;

/**
 * Утилиты для работы с РЭБ (радиоэлектронная борьба).
 * Содержит общие методы расчёта помех и глушения сигнала.
 */
@OnlyIn(Dist.CLIENT)
public final class RebUtils {
    
    private RebUtils() {}
    
    /**
     * Возвращает коэффициент воздействия РЭБ на сущность (0.0 - 1.0).
     * 0.0 = нет эффекта, 1.0 = максимальное воздействие (в центре РЭБ).
     * Использует квадратичную функцию для плавного затухания.
     * 
     * @param entity сущность для проверки
     * @return коэффициент воздействия
     */
    public static double getRebFactor(Entity entity) {
        Level level = entity.level();
        if (level == null) return 0.0;
        
        double rebRadius = ServerConfig.REB_RADIUS.get();
        double rebMiniRadius = ServerConfig.REB_MINI_RADIUS.get();
        double maxFactor = 0.0;
        
        var entities = com.atsuishio.superbwarfare.tools.EntityFindUtil.getEntities(level);
        
        for (var e : entities.getAll()) {
            // Проверяем большой РЭБ
            if (e instanceof RebEntity reb && !reb.isRemoved() && reb.isEnabled()) {
                double distance = Math.sqrt(entity.distanceToSqr(reb));
                if (distance <= rebRadius) {
                    double factor = calculateFactor(distance, rebRadius);
                    maxFactor = Math.max(maxFactor, factor);
                }
            }
            // Проверяем мини РЭБ
            if (e instanceof RebMiniEntity rebMini && !rebMini.isRemoved() && rebMini.isEnabled()) {
                double distance = Math.sqrt(entity.distanceToSqr(rebMini));
                if (distance <= rebMiniRadius) {
                    double factor = calculateFactor(distance, rebMiniRadius);
                    maxFactor = Math.max(maxFactor, factor);
                }
            }
        }
        
        return Math.min(1.0, maxFactor);
    }
    
    /**
     * Вычисляет коэффициент воздействия по расстоянию.
     * Квадратичная функция для реалистичного затухания.
     */
    private static double calculateFactor(double distance, double radius) {
        double normalized = distance / radius;
        double factor = 1.0 - normalized;
        return factor * factor; // Квадратичное затухание
    }
    
    /**
     * Проверяет, находится ли сущность в зоне действия РЭБ.
     */
    public static boolean isInRebZone(Entity entity) {
        return getRebFactor(entity) > 0.0;
    }
    
    /**
     * Проверяет, полностью ли заглушена сущность (в центре РЭБ).
     */
    public static boolean isFullyJammed(Entity entity) {
        return getRebFactor(entity) >= 0.9;
    }
}
