package ru.liko.wrbdrones.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AddonDroneItem extends Item {

    private final Supplier<? extends EntityTypeAccessor> entityTypeSupplier;
    private final Consumer<AddonDroneEntity> loadout;

    public AddonDroneItem(Properties properties,
                          Supplier<? extends EntityTypeAccessor> entityTypeSupplier,
                          Consumer<AddonDroneEntity> loadout) {
        super(properties);
        this.entityTypeSupplier = entityTypeSupplier;
        this.loadout = loadout;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clickedPos);
        BlockPos placePos = state.getCollisionShape(level, clickedPos).isEmpty() ? clickedPos : clickedPos.relative(face);

        Entity entity = entityTypeSupplier.get().spawn(serverLevel, stack, context.getPlayer(), placePos,
                MobSpawnType.SPAWN_EGG, true, !Objects.equals(clickedPos, placePos) && face == Direction.UP);
        if (entity != null) {
            stack.shrink(1);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clickedPos);
            applyLoadout(entity);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        BlockPos pos = hitResult.getBlockPos();
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hitResult.getDirection(), stack)) {
            return InteractionResultHolder.fail(stack);
        }

        Entity entity = entityTypeSupplier.get().spawn(serverLevel, stack, player, pos, MobSpawnType.SPAWN_EGG, false, false);
        if (entity == null) {
            return InteractionResultHolder.pass(stack);
        }

        applyLoadout(entity);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        level.gameEvent(player, GameEvent.ENTITY_PLACE, entity.position());
        return InteractionResultHolder.consume(stack);
    }

    private void applyLoadout(Entity entity) {
        if (entity instanceof AddonDroneEntity addon) {
            if (loadout != null) {
                loadout.accept(addon);
            } else {
                addon.applySpawnLoadout();
            }
        }
    }

    @FunctionalInterface
    public interface EntityTypeAccessor {
        Entity spawn(ServerLevel level, ItemStack stack, Player player, BlockPos pos,
                     MobSpawnType spawnType, boolean alignPosition, boolean waterPlace);
    }

    public static EntityTypeAccessor fromType(Supplier<? extends EntityType<? extends AddonDroneEntity>> typeSupplier) {
        return (level, stack, player, pos, spawnType, alignPosition, waterPlace) ->
                typeSupplier.get().spawn(level, stack, player, pos, spawnType, alignPosition, waterPlace);
    }
}
