package ca.bradj.questown.blocks;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.town.TownWorkStatusStore;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.List;

import static ca.bradj.questown.blocks.JobBlock.*;

public class OreProcessingBlock extends Block implements StatefulJobBlock {
    public static final String ITEM_ID = "ore_processing_block";

    public OreProcessingBlock(
    ) {
        super(
                Properties
                        .of(Material.WOOL, MaterialColor.COLOR_BROWN)
                        .strength(1.0F, 10.0F)
        );
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PROCESSING_STATE, 0)
                .setValue(WORK_LEFT, 0)
        );
    }

    @Override
    public void setProcessingState(
            ServerLevel sl,
            BlockPos pp,
            TownWorkStatusStore.State bs) {
        BlockState oldState = sl.getBlockState(pp);
        oldState.setValue(PROCESSING_STATE, bs.processingState());
        oldState.setValue(WORK_LEFT, bs.workLeft());
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // FIXME: Also drop stuff inside
        return ImmutableList.of(ItemsInit.ORE_PROCESSING_BLOCK.get().getDefaultInstance());
    }

    // TODO: Bring back fire animation

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(PROCESSING_STATE, 0)
                .setValue(WORK_LEFT, 0);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(PROCESSING_STATE, WORK_LEFT);
    }

}
