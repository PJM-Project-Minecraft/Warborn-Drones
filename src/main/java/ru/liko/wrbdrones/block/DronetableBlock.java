package ru.liko.wrbdrones.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.block.entity.DronetableBlockEntity;
import ru.liko.wrbdrones.registry.ModBlockEntities;
import ru.liko.wrbdrones.registry.ModBlocks;

public class DronetableBlock extends BaseEntityBlock {
    public static final MapCodec<DronetableBlock> CODEC = simpleCodec(DronetableBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    // Задняя грань модели подрезана до края блока (16/0), чтобы сзади стола не возникал
    // лишний хитбокс из-за 3.6-пиксельного выступа и можно было ставить блок вплотную.
    private static final double[] NORTH_BOUNDS = {-8.0, 0.0, 0.0, 24.0, 33.0, 16.0};
    private static final double[] EAST_BOUNDS = {0.0, 0.0, -8.0, 16.0, 33.0, 24.0};
    private static final double[] SOUTH_BOUNDS = {-8.0, 0.0, 0.0, 24.0, 33.0, 16.0};
    private static final double[] WEST_BOUNDS = {0.0, 0.0, -8.0, 16.0, 33.0, 24.0};

    public DronetableBlock() {
        this(Properties.of().strength(2.0f).requiresCorrectToolForDrops().noOcclusion()
                .pushReaction(PushReaction.DESTROY));
    }

    public DronetableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        // Контур выделения — единая форма всей модели (общая для всех блоков мультиблока),
        // чтобы стол подсвечивался как одно целое, а не отдельными секциями.
        return getModelOutline(state.getValue(FACING), 0, 0, 0);
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return getModelSlice(state.getValue(FACING), 0, 0, 0);
    }

    @Override
    protected @NotNull VoxelShape getInteractionShape(@NotNull BlockState state, @NotNull BlockGetter level,
            @NotNull BlockPos pos) {
        return getModelSlice(state.getValue(FACING), 0, 0, 0);
    }

    @Override
    protected boolean isPathfindable(@NotNull BlockState state, @NotNull PathComputationType type) {
        return false;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
            @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DronetableBlockEntity dronetable && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(dronetable, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlaceHitboxes(context.getLevel(), context.getClickedPos(), facing)
                ? this.defaultBlockState().setValue(FACING, facing)
                : null;
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            placeHitboxes(level, pos, state.getValue(FACING));
        }
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
            @NotNull BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            removeHitboxes(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DronetableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(@NotNull Level level,
            @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        return level.isClientSide() ? null
                : createTickerHelper(blockEntityType, ModBlockEntities.DRONETABLE.get(), DronetableBlock::serverTick);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    private static void serverTick(Level level, BlockPos pos, BlockState state, DronetableBlockEntity blockEntity) {
        if (level.getGameTime() % 40L == 0L) {
            placeHitboxes(level, pos, state.getValue(FACING));
        }
    }

    static VoxelShape getModelSlice(Direction facing, int offsetX, int offsetY, int offsetZ) {
        double[] bounds = getModelBounds(facing);
        double minX = clamp(bounds[0] - offsetX * 16.0, 0.0, 16.0);
        double minY = clamp(bounds[1] - offsetY * 16.0, 0.0, 16.0);
        double minZ = clamp(bounds[2] - offsetZ * 16.0, 0.0, 16.0);
        double maxX = clamp(bounds[3] - offsetX * 16.0, 0.0, 16.0);
        double maxY = clamp(bounds[4] - offsetY * 16.0, 0.0, 16.0);
        double maxZ = clamp(bounds[5] - offsetZ * 16.0, 0.0, 16.0);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return Shapes.empty();
        }
        return Block.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Полная форма модели (без обрезки по границам блока) относительно блока со смещением
     * {@code offset} от корня мультиблока. Возвращается в локальных координатах данного блока,
     * поэтому в мировых координатах форма совпадает для корня и всех хитбоксов — единый контур.
     */
    static VoxelShape getModelOutline(Direction facing, int offsetX, int offsetY, int offsetZ) {
        double[] bounds = getModelBounds(facing);
        return Shapes.create(new AABB(
                (bounds[0] - offsetX * 16.0) / 16.0,
                (bounds[1] - offsetY * 16.0) / 16.0,
                (bounds[2] - offsetZ * 16.0) / 16.0,
                (bounds[3] - offsetX * 16.0) / 16.0,
                (bounds[4] - offsetY * 16.0) / 16.0,
                (bounds[5] - offsetZ * 16.0) / 16.0));
    }

    private static double[] getModelBounds(Direction facing) {
        return switch (facing) {
            case EAST -> EAST_BOUNDS;
            case SOUTH -> SOUTH_BOUNDS;
            case WEST -> WEST_BOUNDS;
            default -> NORTH_BOUNDS;
        };
    }

    private static boolean canPlaceHitboxes(Level level, BlockPos pos, Direction facing) {
        for (BlockPos offset : getHitboxOffsets(facing)) {
            BlockState state = level.getBlockState(pos.offset(offset));
            if (!state.isAir() && !state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    private static void placeHitboxes(Level level, BlockPos pos, Direction facing) {
        for (BlockPos offset : getHitboxOffsets(facing)) {
            BlockPos hitboxPos = pos.offset(offset);
            BlockState state = level.getBlockState(hitboxPos);
            if (state.isAir() || state.canBeReplaced()) {
                level.setBlock(hitboxPos, ModBlocks.DRONETABLE_HITBOX.get().defaultBlockState()
                        .setValue(DronetableHitboxBlock.FACING, facing)
                        .setValue(DronetableHitboxBlock.OFFSET_X, offset.getX() + DronetableHitboxBlock.OFFSET_BIAS)
                        .setValue(DronetableHitboxBlock.OFFSET_Y, offset.getY())
                        .setValue(DronetableHitboxBlock.OFFSET_Z, offset.getZ() + DronetableHitboxBlock.OFFSET_BIAS), Block.UPDATE_ALL);
            }
        }
    }

    private static void removeHitboxes(Level level, BlockPos pos, Direction facing) {
        for (BlockPos offset : getHitboxOffsets(facing)) {
            BlockPos hitboxPos = pos.offset(offset);
            BlockState state = level.getBlockState(hitboxPos);
            if (state.is(ModBlocks.DRONETABLE_HITBOX.get()) && DronetableHitboxBlock.getParentPos(hitboxPos, state)
                    .equals(pos)) {
                level.setBlock(hitboxPos, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    private static BlockPos[] getHitboxOffsets(Direction facing) {
        double[] bounds = getModelBounds(facing);
        int minX = cellMin(bounds[0]);
        int minY = cellMin(bounds[1]);
        int minZ = cellMin(bounds[2]);
        int maxX = cellMax(bounds[3]);
        int maxY = cellMax(bounds[4]);
        int maxZ = cellMax(bounds[5]);
        BlockPos[] offsets = new BlockPos[(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) - 1];
        int index = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        offsets[index++] = new BlockPos(x, y, z);
                    }
                }
            }
        }
        return offsets;
    }

    private static int cellMin(double value) {
        return (int) Math.floor(value / 16.0);
    }

    private static int cellMax(double value) {
        return (int) Math.floor((value - 0.001) / 16.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
