package ca.bradj.questown.blocks;

import ca.bradj.questown.blocks.entity.FoodDisplayEntity;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.MCExtra;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FoodDisplayBlock extends HorizontalDirectionalBlock implements InsertedItemAware, EntityBlock {
    public static final String ITEM_ID = "food_display";

    public FoodDisplayBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOD, MaterialColor.WOOD)
                        .strength(2.0F, 3.0F)
                        .sound(SoundType.WOOD)
                        .noOcclusion()
        );
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(FACING);
    }

    @Override
    public void onRemove(
            BlockState p_51538_,
            Level p_51539_,
            BlockPos pos,
            BlockState p_51541_,
            boolean p_51542_
    ) {
        if (!p_51538_.is(p_51541_.getBlock())) {
            BlockEntity blockentity = p_51539_.getBlockEntity(pos);
            if (blockentity instanceof FoodDisplayEntity fde) {
                if (p_51539_ instanceof ServerLevel sl) {
                    dropAllLoot(pos, fde, sl);
                }
                p_51539_.updateNeighbourForOutputSignal(pos, this);
            }

            super.onRemove(p_51538_, p_51539_, pos, p_51541_, p_51542_);
        }

    }

    private static void dropAllLoot(
            BlockPos pos,
            FoodDisplayEntity fde,
            ServerLevel sl
    ) {
        for (ItemStack item : fde.getItems()) {
            sl.addFreshEntity(new ItemEntity(sl, pos.getX(), pos.getY(), pos.getZ(), item));
        }
        ItemStack item = ItemsInit.FOOD_DISPLAY_BLOCK.get().getDefaultInstance();
        sl.addFreshEntity(new ItemEntity(sl, pos.getX(), pos.getY(), pos.getZ(), item));
    }

    @Override
    public void handleInsertedItem(
            MCExtra extra,
            BlockPos bp,
            MCHeldItem item
    ) {
        ServerLevel sl = extra.town().getServerLevel();
        BlockState bs = sl.getBlockState(bp);
        sl.setBlockAndUpdate(bp, bs);
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level p_60504_,
            BlockPos pos,
            Player p_60506_,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {

        if (!(p_60504_ instanceof ServerLevel sl)) {
            return InteractionResult.PASS;
        }

        if (!InteractionHand.MAIN_HAND.equals(p_60507_)) {
            return InteractionResult.PASS;
        }

        ItemStack item = p_60506_.getItemInHand(p_60507_);

        BlockEntity blockentity = sl.getBlockEntity(pos);
        if (!(blockentity instanceof FoodDisplayEntity fde)) {
            return InteractionResult.PASS;
        }

        if (item.isEmpty()) {
            ItemStack removed = fde.removeFood();
            p_60506_.addItem(removed);
            return InteractionResult.CONSUME;
        }

        if (!Ingredient.of(TagsInit.Items.VILLAGER_FOOD).test(item)) {
            return InteractionResult.PASS;
        }

        if (fde.addFood(new ItemStack(item.getItem(), 1))) {
            item.shrink(1);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(
            BlockPos blockPos,
            BlockState blockState
    ) {
        return new FoodDisplayEntity(blockPos, blockState);
    }

    @Override
    public RenderShape getRenderShape(BlockState blockState) {
        return RenderShape.MODEL;
    }
}
