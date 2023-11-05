package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.items.ItemsInit;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class OreProcessingBlock extends HorizontalDirectionalBlock {
    public static final String ITEM_ID = "ore_processing_block";

    public OreProcessingBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
        );
    }
//
//    @Override
//    public BlockState insertItem(
//            TownInterface level,
//            BlockPos bp,
//            ItemStack item,
//            int workToNextStep
//    ) {
//        TownJobHandle.State oldState = level.getJobBlockState(bp);
//
//        // FIXME: Check item type and state
//        int curValue = oldState.getValue(PROCESSING_STATE);
//        if (
//                canAcceptOre(level, bp) && item.is(Items.IRON_ORE) // TODO: Support more ores
//        ) {
//            item.shrink(1);
//            int val = curValue + 1;
//            BlockState blockState = setProcessingState(oldState, val);
//            if (val == BAKE_STATE_FILLED) {
//                blockState = blockState.setValue(WORK_LEFT, Config.SMELTER_WORK_REQUIRED.get());
//            }
//            level.setJobBlockState(bp, blockState);
//            return blockState;
//        }
//        return oldState;
//    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // FIXME: Also drop stuff inside
        return ImmutableList.of(ItemsInit.ORE_PROCESSING_BLOCK.get().getDefaultInstance());
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState blockState = this.defaultBlockState();
        if (!(ctx.getLevel() instanceof ServerLevel sl)) {
            return blockState;
        }
        return blockState
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    // TODO: Bring back fire animation

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

//        if (hasOreToCollect(sl, pos)) {
//            moveOreToWorld(sl, pos, is -> player.getInventory().add(is));
//            return InteractionResult.sidedSuccess(level.isClientSide);
//        }

//        if (canAcceptOre(sl, pos)) {
//            player.sendMessage(
//                    new TranslatableComponent("message.smelter.villagers_will_add_ore"),
//                    player.getUUID()
//            );
//            return InteractionResult.sidedSuccess(level.isClientSide);
//        }
//
//        if (canAcceptWork(sl, pos)) {
//            player.sendMessage(
//                    new TranslatableComponent("message.smelter.villagers_will_process"),
//                    player.getUUID()
//            );
//            return InteractionResult.sidedSuccess(level.isClientSide);
//        }
//
//        player.sendMessage(
//                new TranslatableComponent("message.smelter.processing"),
//                player.getUUID()
//        );
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
