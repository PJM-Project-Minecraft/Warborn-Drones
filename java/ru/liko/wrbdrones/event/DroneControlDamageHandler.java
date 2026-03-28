package ru.liko.wrbdrones.event;

import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;
import ru.liko.wrbdrones.util.PlayerDecoyManager;

/**
 * Обработчик урона для игроков, управляющих дронами.
 * 
 * Во время управления дроном:
 * - Реальный игрок становится невидимым и не получает прямой урон
 * - Декой (копия игрока) получает урон и передаёт его реальному игроку
 * - Урон от декоя НЕ отменяется, чтобы система работала корректно
 */
public final class DroneControlDamageHandler {

    private DroneControlDamageHandler() {}

    private static boolean isControllingDrone(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) return false;
        var tag = stack.getOrCreateTag();
        return tag.getBoolean("Using") && tag.getBoolean("Linked");
    }

    private static boolean isDamageFromDecoy(DamageSource source) {
        return source.getEntity() instanceof PlayerDecoyEntity;
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        
        // Если у игрока есть декой и урон НЕ от декоя - отменяем
        // (урон от декоя - это передача урона от декоя к игроку)
        if (isControllingDrone(player) && PlayerDecoyManager.hasDecoy(player.getUUID())) {
            if (!isDamageFromDecoy(event.getSource())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        
        // Если у игрока есть декой и урон НЕ от декоя - отменяем
        if (isControllingDrone(player) && PlayerDecoyManager.hasDecoy(player.getUUID())) {
            if (!isDamageFromDecoy(event.getSource())) {
                event.setCanceled(true);
            }
        }
    }
}


