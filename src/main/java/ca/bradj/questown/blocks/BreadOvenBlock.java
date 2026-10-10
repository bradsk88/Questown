package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.MapC;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A furnace-shaped processing block for the baker job family: the oven bakes
 * 1 wheat + 1 coal into 1 bread over {@link BreadOvenBake#BAKE_TICKS} ticks,
 * with the items stored in the block entity's slots (0 wheat, 1 coal,
 * 2 bread) so the cook-shaped job slot rules
 * ({@code insert_into_slot_0/1}, {@code take_from_slot_2}) can drive it.
 *
 * <p>The {@code bake_state} property is the visual (unchanged art) and is kept
 * in sync with the slot contents by {@link BreadOvenBlockEntity}, not by the
 * townie's work-state index.
 */
public class BreadOvenBlock extends BaseEntityBlock {

    public static final MapCodec<BreadOvenBlock> CODEC = MapCodec.unit(BreadOvenBlock::new);

    @Override
    public MapCodec<BreadOvenBlock> codec() {
        return CODEC;
    }

    public static final String ITEM_ID = "bread_oven_block";

    public static final IntegerProperty BAKE_STATE = IntegerProperty.create(
            "bake_state", 0, 4
    );

    public static final int BAKE_STATE_EMPTY = 0;
    public static final int BAKE_STATE_HALF_FILLED = 1;
    public static final int BAKE_STATE_FILLED = 2;
    public static final int BAKE_STATE_BAKING = 3;
    public static final int BAKE_STATE_BAKED = 4;

    public BreadOvenBlock() {
        super(
                Compat.P
                        .of().mapColor(MapC.COLOR_BROWN)
                        .lightLevel((BlockState bs) -> bs.getValue(BAKE_STATE) == BAKE_STATE_BAKING ? 10 : 0)
                        .strength(1.0F, 10.0F).impl()
        );
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BAKE_STATE, 0)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
        );
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(BAKE_STATE, HorizontalDirectionalBlock.FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState().setValue(BAKE_STATE, 0);
        if (!(ctx.getLevel() instanceof net.minecraft.server.level.ServerLevel)) {
            return blockState;
        }
        return blockState
                .setValue(HorizontalDirectionalBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootParams.Builder p_60538_
    ) {
        return List.of(this.defaultBlockState().getBlock().asItem().getDefaultInstance());
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (!newState.is(this)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BreadOvenBlockEntity oven) {
                for (int i = 0; i < BreadOvenBake.SLOT_BREAD + 1; i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), oven.getItem(i));
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void animateTick(
            BlockState bs,
            Level level,
            BlockPos pos,
            RandomSource random
    ) {
        if (bs.getValue(BAKE_STATE) == BAKE_STATE_BAKING) {
            double d0 = (double) pos.getX() + 0.5D;
            double d1 = (double) pos.getY();
            double d2 = (double) pos.getZ() + 0.5D;
            if (random.nextDouble() < 0.1D) {
                level.playSound(
                        null,
                        d0,
                        d1,
                        d2,
                        SoundEvents.FURNACE_FIRE_CRACKLE,
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F
                );
            }

            Direction direction = bs.getValue(HorizontalDirectionalBlock.FACING);
            Direction.Axis direction$axis = direction.getAxis();
            double d3 = 0.52D;
            double d4 = random.nextDouble() * 0.6D - 0.3D;
            double d5 = direction$axis == Direction.Axis.X ? (double) direction.getStepX() * 0.52D : d4;
            double d6 = direction$axis == Direction.Axis.Z ? (double) direction.getStepZ() * 0.52D : d4;
            double d7 = direction$axis == Direction.Axis.X ? d4 : d6;
            level.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + 0.6D, d2 + d7, 0.0D, 0.0D, 0.0D);
            level.addParticle(ParticleTypes.FLAME, d0 + d5, d1 + 0.5D, d2 + d7, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState blockState,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult p_60508_
    ) {
        if (level.isClientSide) {
            return InteractionResult.sidedSuccess(true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BreadOvenBlockEntity oven) {
            ItemStack bread = oven.takeBread(1);
            if (bread != null) {
                player.getInventory().add(bread);
            }
        }
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreadOvenBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState blockState,
            BlockEntityType<T> entityType
    ) {
        return level.isClientSide ? null : createTickerHelper(
                entityType, TilesInit.BREAD_OVEN.get(), BreadOvenBlockEntity::tick
        );
    }
}
