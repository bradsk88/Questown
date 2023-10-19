package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.init.items.ItemsInit;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Random;

public class BreadOvenBlock extends HorizontalDirectionalBlock implements ScheduledBlock {
    public static final String ITEM_ID = "bread_oven_block";

    public static final IntegerProperty BAKE_STATE = IntegerProperty.create(
            "bake_state", 0, 4
    );

    private static final int BAKE_STATE_EMPTY = 0;
    private static final int BAKE_STATE_HALF_FILLED = 1;
    private static final int BAKE_STATE_FILLED = 2;
    private static final int BAKE_STATE_BAKING = 3;
    private static final int BAKE_STATE_BAKED = 4;

    public static final IntegerProperty STARTED_BAKING_AT = IntegerProperty.create(
            "stared_baking_at", 0, 24
    );

    public BreadOvenBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .lightLevel((BlockState bs) -> bs.getValue(BAKE_STATE) == BAKE_STATE_BAKING ? 10 : 0)
                        .strength(1.0F, 10.0F)
        );
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BAKE_STATE, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(STARTED_BAKING_AT, 0)
        );
    }

    public static boolean canAddIngredients(BlockState blockState) {
        return canAcceptWheat(blockState) || canAcceptCoal(blockState);
    }

    public static BlockState insertItem(
            ServerLevel level,
            BlockState oldState,
            ItemStack item
    ) {
        // FIXME: Check item type and state
        int curValue = oldState.getValue(BAKE_STATE);
        if (
                canAcceptWheat(oldState) && item.is(Items.WHEAT) ||
                        canAcceptCoal(oldState) && item.is(Items.COAL)
        ) {
            item.shrink(1);
            int val = curValue + 1;
            BlockState blockState = oldState.setValue(BAKE_STATE, val);
            if (val == BAKE_STATE_BAKING) {
                int dayHour = (int) (level.getDayTime() % 24000) / 1000;
                blockState = setBakingHour(blockState, dayHour);
            }
            return blockState;
        }
        return oldState;
    }

    public static boolean canAcceptWheat(BlockState oldState) {
        if (!oldState.hasProperty(BAKE_STATE)) {
            return false;
        }
        return oldState.getValue(BAKE_STATE) < BAKE_STATE_FILLED;
    }

    public static boolean canAcceptCoal(BlockState oldState) {
        if (!oldState.hasProperty(BAKE_STATE)) {
            return false;
        }
        return oldState.getValue(BAKE_STATE) == BAKE_STATE_FILLED;
    }

    public static boolean isBaking(BlockState oldState) {
        if (!oldState.hasProperty(BAKE_STATE)) {
            return false;
        }
        return oldState.getValue(BAKE_STATE) == BAKE_STATE_BAKING;
    }

    public static boolean hasBread(BlockState oldState) {
        if (!oldState.hasProperty(BAKE_STATE)) {
            return false;
        }
        return oldState.getValue(BAKE_STATE) == BAKE_STATE_BAKED;
    }

    public interface TakeFn {
        boolean Take(ItemStack is);
    }

    public static BlockState extractBread(
            BlockState oldState,
            ServerLevel sl,
            BlockPos block,
            @Nullable TakeFn takeFn
    ) {
        BlockState bs = oldState.setValue(BAKE_STATE, BAKE_STATE_EMPTY);
        sl.setBlock(block, bs, 11);
        moveBreadToWorld(sl, block, takeFn);
        return bs;
    }

    private static void moveBreadToWorld(
            ServerLevel level,
            BlockPos b,
            @Nullable TakeFn takeFn
    ) {
        ItemStack is = new ItemStack(Items.BREAD, 1);
        if (takeFn == null || !takeFn.Take(is)) {
            level.addFreshEntity(new ItemEntity(level, b.getX(), b.getY(), b.getZ(), is));
        }
        level.setBlock(b, level.getBlockState(b).setValue(BAKE_STATE, BAKE_STATE_EMPTY), 11);
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        return ImmutableList.of(ItemsInit.BREAD_OVEN_BLOCK.get().getDefaultInstance());
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState().setValue(BAKE_STATE, 0);
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return blockState;
        }
        return blockState
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(STARTED_BAKING_AT, 0);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(BAKE_STATE, FACING, STARTED_BAKING_AT);
    }


    public void animateTick(
            BlockState p_53635_,
            Level p_53636_,
            BlockPos p_53637_,
            Random p_53638_
    ) {
        if (p_53635_.getValue(BAKE_STATE) == BAKE_STATE_BAKING) {
            double d0 = (double) p_53637_.getX() + 0.5D;
            double d1 = (double) p_53637_.getY();
            double d2 = (double) p_53637_.getZ() + 0.5D;
            if (p_53638_.nextDouble() < 0.1D) {
                p_53636_.playLocalSound(
                        d0,
                        d1,
                        d2,
                        SoundEvents.FURNACE_FIRE_CRACKLE,
                        SoundSource.BLOCKS,
                        1.0F,
                        1.0F,
                        false
                );
            }

            Direction direction = p_53635_.getValue(FACING);
            Direction.Axis direction$axis = direction.getAxis();
            double d3 = 0.52D;
            double d4 = p_53638_.nextDouble() * 0.6D - 0.3D;
            double d5 = direction$axis == Direction.Axis.X ? (double) direction.getStepX() * 0.52D : d4;
            double d6 = p_53638_.nextDouble() * 6.0D / 16.0D;
            double d7 = direction$axis == Direction.Axis.Z ? (double) direction.getStepZ() * 0.52D : d4;
            p_53636_.addParticle(ParticleTypes.SMOKE, d0 + d5, d1 + d6, d2 + d7, 0.0D, 0.0D, 0.0D);
            p_53636_.addParticle(ParticleTypes.FLAME, d0 + d5, d1 + d6, d2 + d7, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public InteractionResult use(
            BlockState blockState,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        if (!(level instanceof ServerLevel sl)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (hasBread(blockState)) {
            moveBreadToWorld(sl, pos, is -> player.getInventory().add(is));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (canAcceptWheat(blockState)) {
            player.sendMessage(
                    new TranslatableComponent("message.baker.villagers_will_add_wheat"),
                    player.getUUID()
            );
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (canAcceptCoal(blockState)) {
            player.sendMessage(
                    new TranslatableComponent("message.baker.villagers_will_add_coal"),
                    player.getUUID()
            );
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!blockState.hasProperty(STARTED_BAKING_AT)) {
            if (isBaking(blockState)) {
                blockState = setBakingHour(blockState, calculateCurrentHour(sl));
                level.setBlock(pos, blockState, 11);
            } else {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        int startTick = blockState.getValue(STARTED_BAKING_AT) * 1000;
        int endTick = startTick + Config.BAKING_TIME.get();
        long ticksLeft = endTick - (level.getDayTime() % 24000);

        player.sendMessage(
                new TranslatableComponent("message.baker.bread_still_baking", ticksLeft),
                player.getUUID()
        );
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockState tryAdvance(
            ServerLevel level,
            BlockState blockState
    ) {
        int hour = calculateCurrentHour(level);
        if (!blockState.hasProperty(STARTED_BAKING_AT)) {
            if (isBaking(blockState)) {
                return setBakingHour(blockState, hour);
            }
        }

        Integer started = blockState.getValue(STARTED_BAKING_AT);

        if (isBaking(blockState)) {
            if (started == 0) {
                return setBakingHour(blockState, hour);
            }

            int plus = Config.BAKING_TIME.get() / 1000;
            int doneHour = started + plus;
            if (hour >= doneHour) {
                return blockState.setValue(BAKE_STATE, BAKE_STATE_BAKED);
            }
        }

        return null;
    }

    private static BlockState setBakingHour(BlockState bs, int hour) {
        QT.BLOCK_LOGGER.debug("Set baking hour to {}", hour);
        return bs.setValue(STARTED_BAKING_AT, hour);
    }

    private static int calculateCurrentHour(ServerLevel level) {
        return (int) (level.getDayTime() % 24000) / 1000;
    }
}
