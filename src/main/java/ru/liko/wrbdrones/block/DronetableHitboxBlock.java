package ru.liko.wrbdrones.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.block.entity.DronetableBlockEntity;
import ru.liko.wrbdrones.registry.ModBlocks;
import ru.liko.wrbdrones.registry.ModItems;

public class DronetableHitboxBlock extends Block {
    public static final MapCodec<DronetableHitboxBlock> CODEC = simpleCodec(DronetableHitboxBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    // Vanilla IntegerProperty не допускает отрицательный минимум, поэтому реальное смещение
    // (-1..1) хранится со сдвигом OFFSET_BIAS, давая допустимый диапазон 0..2.
    public static final int OFFSET_BIAS = 1;
    public static final IntegerProperty OFFSET_X = IntegerProperty.create("offset_x", 0, 2);
    public static final IntegerProperty OFFSET_Y = IntegerProperty.create("offset_y", 0, 2);
    public static final IntegerProperty OFFSET_Z = IntegerProperty.create("offset_z", 0, 2);

    public DronetableHitboxBlock() {
        this(Properties.of().strength(2.0f).noOcclusion().pushReaction(PushReaction.DESTROY));
    }

    public DronetableHitboxBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OFFSET_X, OFFSET_BIAS)
                .setValue(OFFSET_Y, 0)
                .setValue(OFFSET_Z, OFFSET_BIAS));
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        // Контур выделения — единая форма всей модели (общая для корня и всех хитбоксов),
        // чтобы стол подсвечивался как одно целое, а не отдельными секциями.
        return DronetableBlock.getModelOutline(state.getValue(FACING), state.getValue(OFFSET_X) - OFFSET_BIAS,
                state.getValue(OFFSET_Y), state.getValue(OFFSET_Z) - OFFSET_BIAS);
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return DronetableBlock.getModelSlice(state.getValue(FACING), state.getValue(OFFSET_X) - OFFSET_BIAS,
                state.getValue(OFFSET_Y), state.getValue(OFFSET_Z) - OFFSET_BIAS);
    }

    @Override
    protected @NotNull VoxelShape getInteractionShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos) {
        return getCollisionShape(state, level, pos, CollisionContext.empty());
    }

    @Override
    protected boolean isPathfindable(@NotNull BlockState state, @NotNull PathComputationType type) {
        return false;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
        BlockPos parentPos = getParentPos(pos, state);
        if (level.isClientSide()) {
            return level.getBlockState(parentPos).is(ModBlocks.DRONETABLE.get()) ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        }

        BlockEntity blockEntity = level.getBlockEntity(parentPos);
        if (blockEntity instanceof DronetableBlockEntity dronetable && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(dronetable, parentPos);
            return InteractionResult.CONSUME;
        }

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos parentPos = getParentPos(pos, state);
            if (level.getBlockState(parentPos).is(ModBlocks.DRONETABLE.get())) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.gameMode.destroyBlock(parentPos);
                } else {
                    level.destroyBlock(parentPos, false, player);
                }
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return state;
    }

    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull Block neighborBlock, @NotNull BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide() && !level.getBlockState(getParentPos(pos, state)).is(ModBlocks.DRONETABLE.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull LevelReader level, @NotNull BlockPos pos,
            @NotNull BlockState state) {
        return new ItemStack(ModItems.DRONETABLE.get());
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING, OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    public static BlockPos getParentPos(BlockPos pos, BlockState state) {
        return pos.offset(-(state.getValue(OFFSET_X) - OFFSET_BIAS), -state.getValue(OFFSET_Y),
                -(state.getValue(OFFSET_Z) - OFFSET_BIAS));
    }
}
