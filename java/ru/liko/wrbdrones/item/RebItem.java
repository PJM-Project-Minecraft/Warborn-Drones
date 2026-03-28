package ru.liko.wrbdrones.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.registry.ModEntityTypes;

import java.util.Objects;

public class RebItem extends Item {
    public RebItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos clickedPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clickedPos);
        BlockPos placePos = state.getCollisionShape(level, clickedPos).isEmpty() 
                ? clickedPos 
                : clickedPos.relative(face);

        var entityType = ModEntityTypes.REB.get();
        var entity = entityType.spawn(serverLevel, stack, context.getPlayer(), placePos,
                MobSpawnType.SPAWN_EGG, true, !Objects.equals(clickedPos, placePos) && face == Direction.UP);
        
        if (entity != null) {
            // Устанавливаем поворот на основе направления взгляда игрока
            if (context.getPlayer() != null) {
                entity.setYRot(context.getPlayer().getYRot() + 180.0F);
            }
            stack.shrink(1);
            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, clickedPos);
        }

        return InteractionResult.CONSUME;
    }
}

