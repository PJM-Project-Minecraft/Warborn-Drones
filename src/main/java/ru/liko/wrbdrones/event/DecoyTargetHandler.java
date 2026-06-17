package ru.liko.wrbdrones.event;

import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;
import ru.liko.wrbdrones.util.PlayerDecoyManager;

/**
 * Обработчик для добавления PlayerDecoyEntity в список целей мобов.
 * Мобы будут атаковать декой как обычного игрока.
 */
@EventBusSubscriber(modid = Wrbdrones.MODID, bus = EventBusSubscriber.Bus.GAME)
public class DecoyTargetHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof Mob mob) {
            addDecoyTargetGoal(mob);
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof Mob)) {
            return;
        }

        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (!(newTarget instanceof ServerPlayer player)) {
            return;
        }

        if (!isControllingDrone(player)) {
            return;
        }

        PlayerDecoyEntity decoy = PlayerDecoyManager.getDecoy(player.getUUID());
        if (decoy != null && decoy.isAlive()) {
            // Старый режим (Mavic/Lancet): реальный игрок телепортирован к дрону и
            // невидим — перенаправляем моба на декой.
            event.setNewAboutToBeSetTarget(decoy);
        }
        // Декоя нет (FPV self-chunk): пилот остаётся на месте и уязвим — НЕ отменяем
        // таргет, пусть мобы атакуют реальное тело пилота напрямую.
    }

    private static void addDecoyTargetGoal(Mob mob) {
        try {
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
                    mob,
                    PlayerDecoyEntity.class,
                    true,
                    false));
        } catch (Exception ignored) {
        }
    }

    private static boolean isControllingDrone(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return false;
        }
        var tag = NBTTool.getTag(stack);
        return tag.getBoolean("Using") && tag.getBoolean("Linked");
    }
}
