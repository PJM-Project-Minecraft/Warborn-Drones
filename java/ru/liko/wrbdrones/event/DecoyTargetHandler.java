package ru.liko.wrbdrones.event;

import com.atsuishio.superbwarfare.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;
import ru.liko.wrbdrones.util.PlayerDecoyManager;

/**
 * Обработчик для добавления PlayerDecoyEntity в список целей мобов.
 * Мобы будут атаковать декой как обычного игрока.
 */
@Mod.EventBusSubscriber(modid = Wrbdrones.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        LivingEntity newTarget = event.getNewTarget();
        if (!(newTarget instanceof ServerPlayer player)) {
            return;
        }

        if (!isControllingDrone(player)) {
            return;
        }

        PlayerDecoyEntity decoy = PlayerDecoyManager.getDecoy(player.getUUID());
        if (decoy != null && decoy.isAlive()) {
            event.setNewTarget(decoy);
        } else {
            event.setNewTarget(null);
        }
    }

    private static void addDecoyTargetGoal(Mob mob) {
        try {
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
                mob,
                PlayerDecoyEntity.class,
                true,
                false
            ));
        } catch (Exception ignored) {
        }
    }

    private static boolean isControllingDrone(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return false;
        }
        var tag = stack.getOrCreateTag();
        return tag.getBoolean("Using") && tag.getBoolean("Linked");
    }
}
