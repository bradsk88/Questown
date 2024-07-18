package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.declarative.MCExtra;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public class SmallSoupPotBlock extends Block implements InsertedItemAware, StatefulJobBlock {
    public static final String ITEM_ID = "soup_pot_small";

    private static final IntegerProperty LEVEL = IntegerProperty.create(
            "level", 0, 1
    );

    public SmallSoupPotBlock(
    ) {
        super(
                Properties
                        .of(Material.GLASS, MaterialColor.TERRACOTTA_BROWN)
                        .strength(1.0F, 10.0F)
                        .noOcclusion()
        );
        this.registerDefaultState(this.stateDefinition.any()
                                                      .setValue(LEVEL, 0)
        );
    }

    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                   .setValue(LEVEL, 0);
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51385_) {
        p_51385_.add(LEVEL);
    }

    @Override
    public List<ItemStack> getDrops(
            BlockState p_60537_,
            LootContext.Builder p_60538_
    ) {
        // TODO: Preserve level
        return ImmutableList.of(
                ItemsInit.SMALL_SOUP_POT_BLOCK.get().getDefaultInstance()
        );
    }

    @Override
    public void handleInsertedItem(MCExtra extra, BlockPos bp, MCHeldItem item) {
        // TODO: Implement?
    }

    @Override
    public InteractionResult use(
            BlockState p_60503_,
            Level p_60504_,
            BlockPos p_60505_,
            Player p_60506_,
            InteractionHand p_60507_,
            BlockHitResult p_60508_
    ) {
        InteractionResult r = super.use(p_60503_, p_60504_, p_60505_, p_60506_, p_60507_, p_60508_);
        if (p_60504_.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        BlockState p = p_60504_.getBlockState(p_60505_);
        int p61126 = (p.getValue(LEVEL) + 1) % 2;
        p = p.setValue(LEVEL, p61126);
        p_60504_.setBlockAndUpdate(p_60505_, p);
        QT.BLOCK_LOGGER.debug("New level {}", p61126);
        return InteractionResult.CONSUME;
    }

    @Override
    public void setProcessingState(
            ServerLevel sl,
            BlockPos pp,
            State bs
    ) {
        Util.setProcessingStateOnProperty(sl, LEVEL, bs, pp);
    }
}
