package com.yesmenn.creates.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.IAxisPipe;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.yesmenn.creates.block.entity.CheckValveBlockEntity;
import com.yesmenn.creates.registry.YMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;

import java.util.EnumMap;
import java.util.Map;

// make block directional
public class CheckValveBlock extends DirectionalBlock
        implements IBE<CheckValveBlockEntity>, IAxisPipe, IWrenchable, ProperWaterloggedBlock {

    public static final MapCodec<CheckValveBlock> CODEC = simpleCodec(CheckValveBlock::new);

    // rotate fluid axis with block facing
    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            Block.box(4, 4, 0, 12, 12, 16),   // Rohr
            Block.box(3, 3, 0, 13, 13, 1),    // Anschluss vorne
            Block.box(3, 3, 15, 13, 13, 16),  // Anschluss hinten
            Block.box(6, 10, 2, 10, 14, 14)   // Ventilaufsatz
    );

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction dir : Direction.values())
            SHAPES.put(dir, rotateShape(SHAPE_NORTH, dir));
    }

    public CheckValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    public static Direction getOutlet(BlockState state) {
        return state.getValue(FACING);
    }

    public static Direction getInlet(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    // ---------------------------------------------------------------- Shape

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction to) {
        VoxelShape[] result = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            AABB box = new AABB(x1, y1, z1, x2, y2, z2);
            switch (to) {
                case EAST -> box = rotY90(box);
                case SOUTH -> box = rotY90(rotY90(box));
                case WEST -> box = rotY90(rotY90(rotY90(box)));
                case UP -> box = new AABB(box.minX, 1 - box.maxZ, box.minY, box.maxX, 1 - box.minZ, box.maxY);
                case DOWN -> box = new AABB(box.minX, box.minZ, 1 - box.maxY, box.maxX, box.maxZ, 1 - box.minY);
                default -> { }
            }
            result[0] = Shapes.or(result[0], Shapes.create(box));
        });
        return result[0].optimize();
    }

    private static AABB rotY90(AABB b) {
        return new AABB(1 - b.maxZ, b.minY, b.minX, 1 - b.minZ, b.maxY, b.maxX);
    }

    // --------------------- State

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Fluss zeigt vom Spieler weg (in Blickrichtung), mit Sneaken umgekehrt.
        Direction looking = context.getNearestLookingDirection();
        Direction facing = context.isSecondaryUseActive() ? looking.getOpposite() : looking;
        return withWater(defaultBlockState().setValue(FACING, facing), context);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // delete liquid - sourceblock in waterlogged valve

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        BlockState updated = IWrenchable.super.updateAfterWrenched(newState, context);  // Create call
        return updated.setValue(WATERLOGGED, false);
    }

    @Override
    public Direction.Axis getAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // ---------- Create fluid pipe networking

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide && state != oldState)
            level.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = !state.is(newState.getBlock());
        if (blockTypeChanged && !level.isClientSide)
            FluidPropagator.propagateChangedPipe(level, pos, state);
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        DebugPackets.sendNeighborsUpdatePacket(level, pos);
        Direction d = FluidPropagator.validateNeighbourChange(state, level, pos, block, fromPos, isMoving);
        if (d != null && d.getAxis() == getAxis(state))
            level.scheduleTick(pos, this, 1, TickPriority.HIGH);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FluidPropagator.propagateChangedPipe(level, pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        AdvancementBehaviour.setPlacedBy(level, pos, placer);
    }

    // ---------- fluid simulation

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---------------------------------------------------------------- BE

    @Override
    public Class<CheckValveBlockEntity> getBlockEntityClass() {
        return CheckValveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CheckValveBlockEntity> getBlockEntityType() {
        return YMBlockEntities.CHECK_VALVE.get();
    }

    @Override
    protected MapCodec<CheckValveBlock> codec() {
        return CODEC;
    }
}
