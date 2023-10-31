package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
                .setValue(INGREDIENT_COUNT, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(WORK_LEFT, 0)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_49915_) {
        JobBlock.defaultBlockStateDefinition(p_49915_);
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
        Ingredient ingredient = BlacksmithWoodenPickaxeJob.INGREDIENTS_REQUIRED_AT_STATES.get(curValue);
        if (ingredient != null) {
            canDo = ingredient.test(item);
        }
        Integer qtyRequired = BlacksmithWoodenPickaxeJob.INGREDIENT_QTY_REQUIRED_AT_STATES.getOrDefault(curValue, 0);
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
            blockState = blockState.setValue(WORK_LEFT, workToNextStep);
            blockState = blockState.setValue(INGREDIENT_COUNT, 0);
            level.setBlockAndUpdate(bp, blockState);
            return blockState;
        }
        return oldState;
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

        if (Integer.valueOf(BlacksmithWoodenPickaxeJob.MAX_STATE).equals(JobBlock.getState(sl, pos))) {
            moveOreToWorld(sl, pos, is -> player.getInventory().add(is));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // TODO: Generic handling

        return InteractionResult.PASS;
    }
}
