package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.blacksmith.BlacksmithJob;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static ca.bradj.questown.blocks.JobBlock.*;

public class BlacksmithsTableBlock extends HorizontalDirectionalBlock implements ItemAccepting {
    public static final String ITEM_ID = "blacksmiths_table";

    public BlacksmithsTableBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
        );
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PROCESSING_STATE, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(WORK_LEFT, 0)
        );
    }

    @Override
    public BlockState insertItem(
            ServerLevel level,
            BlockPos bp,
            ItemStack item,
            int workToNextStep
    ) {
        BlockState oldState = level.getBlockState(bp);
        int curValue = oldState.getValue(PROCESSING_STATE);
        boolean canDo = false;
        Ingredient ingredient = BlacksmithJob.INGREDIENTS_REQUIRED_AT_STATES.get(curValue);
        if (ingredient != null) {
            canDo = ingredient.test(item);
        }
        Integer qtyRequired = BlacksmithJob.INGREDIENT_QTY_REQUIRED_AT_STATES.getOrDefault(curValue, 0);
        if (qtyRequired == null) {
            qtyRequired = 0;
        }
        Integer curCount = oldState.getValue(INGREDIENT_COUNT);
        if (canDo && curCount >= qtyRequired) {
            QT.BLOCK_LOGGER.error("Somehow exceeded required quantity: can accept up to {}, had {}", qtyRequired, curCount);
        }

        // FIXME: This currently moves the block to the next stage after only one stick

        if (canDo) {
            item.shrink(1);
            int count = curCount + 1;
            BlockState blockState = setIngredientCount(oldState, count);
            if (count < qtyRequired) {
                level.setBlockAndUpdate(bp, blockState);
                return blockState;
            }
            int val = curValue + 1;
            blockState = setProcessingState(blockState, val);
            if (val == BlacksmithJob.BLOCK_STATE_NEED_WORK) {
                blockState = blockState.setValue(WORK_LEFT, workToNextStep);
            }
            level.setBlockAndUpdate(bp, blockState);
            return blockState;
        }
        return oldState;
    }

    private static boolean canAcceptWork(ServerLevel sl, BlockPos bp) {
        BlockState oldState = sl.getBlockState(bp);
        if (!oldState.hasProperty(PROCESSING_STATE)) {
            return false;
        }
        return oldState.getValue(PROCESSING_STATE) == BlacksmithJob.BLOCK_STATE_NEED_WORK;
    }

    private static boolean hasOreToCollect(
            ServerLevel sl,
            BlockPos bp) {
        BlockState oldState = sl.getBlockState(bp);
        if (!oldState.hasProperty(PROCESSING_STATE)) {
            return false;
        }
        return oldState.getValue(PROCESSING_STATE) == BlacksmithJob.BLOCK_STATE_DONE;
    }

    public static BlockState applyWork(
            ServerLevel sl,
            BlockPos bp
    ) {
        if (!canAcceptWork(sl, bp)) {
            throw new IllegalStateException("Cannot apply work at " + bp);
        }

        BlockState oldState = sl.getBlockState(bp);
        int workLeft = oldState.getValue(WORK_LEFT);
        BlockState bs;
        if (workLeft <= 0) {
            bs = setProcessingState(oldState, BlacksmithJob.BLOCK_STATE_DONE);
        } else {
            bs = reduceWorkLeft(oldState);
        }
        if (oldState.equals(bs)) {
            return null;
        }
        sl.setBlockAndUpdate(bp, bs);
        return bs;
    }

    private static BlockState reduceWorkLeft(BlockState oldState) {
        int l = oldState.getValue(WORK_LEFT);
        int newVal = l - 1;
        QT.BLOCK_LOGGER.debug("Setting work_left to {}", newVal);
        return oldState.setValue(WORK_LEFT, newVal);
    }

    private static BlockState setProcessingState(
            BlockState oldState,
            int s
    ) {
        BlockState newState = oldState.setValue(PROCESSING_STATE, s);
        QT.BLOCK_LOGGER.debug("Processing state set to {}", s);
        return newState;
    }

    private static BlockState setIngredientCount(
            BlockState oldState,
            int s
    ) {
        BlockState newState = oldState.setValue(INGREDIENT_COUNT, s);
        QT.BLOCK_LOGGER.debug("Ingredient count set to {}", s);
        return newState;
    }

    private static void moveOreToWorld(
            ServerLevel level,
            BlockPos b,
            @Nullable TakeFn takeFn
    ) {
        ItemStack is = new ItemStack(Items.RAW_IRON, 2);
        Jobs.getOrCreateItemFromBlock(level, b, takeFn, is);
        level.setBlock(b, level.getBlockState(b).setValue(PROCESSING_STATE, 0), 11);
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // FIXME: Also drop stuff inside
        return ImmutableList.of(ItemsInit.ORE_PROCESSING_BLOCK.get().getDefaultInstance());
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState().setValue(PROCESSING_STATE, 0);
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return blockState;
        }
        return blockState
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite())
                .setValue(WORK_LEFT, 0);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(PROCESSING_STATE, FACING, WORK_LEFT);
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

        if (hasOreToCollect(sl, pos)) {
            moveOreToWorld(sl, pos, is -> player.getInventory().add(is));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // TODO: Generic handling

        return InteractionResult.PASS;
    }
}
