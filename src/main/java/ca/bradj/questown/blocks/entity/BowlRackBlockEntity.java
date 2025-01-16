package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.blocks.BowlRackBlock;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BowlRackBlockEntity extends BlockEntity implements ContainerTarget.Container<MCTownItem> {
    public BowlRackBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.BOWL_RACK.get(), p_155229_, p_155230_);
    }

    @Override
    public int size() {
        return BowlRackBlock.getLevel(getBlockState());
    }

    @Override
    public MCTownItem getItem(int i) {
        if (i > BowlRackBlock.getLevel(getBlockState())) {
            return MCTownItem.Air();
        }
        return MCTownItem.fromMCItemStack(Items.BOWL.getDefaultInstance());
    }

    @Override
    public MCTownItem removeItem(int index) {
        MCTownItem item = getItem(index);
        if (!item.isEmpty()) {
            if (getLevel() instanceof ServerLevel sl) {
                BlockState bs = getBlockState();
                int curLevel = BowlRackBlock.getLevel(bs);
                if (curLevel == 0) {
                    return MCTownItem.Air();
                }
                sl.setBlockAndUpdate(getBlockPos(), BowlRackBlock.reduceLevel(bs));
            }
        }
        return item;
    }

    @Override
    public boolean isFull() {
        return BowlRackBlock.isFull(getBlockState());
    }

    @Override
    public String toShortString() {
        return "BowlRack["+BowlRackBlock.getLevel(getBlockState())+"]";
    }

    @Override
    public String toShortString(boolean includeAir) {
        return toShortString();
    }

    @Override
    public boolean canAcceptIfSpaceAllows(MCTownItem item) {
        return item.get().equals(Items.BOWL);
    }

    @Override
    public RankBoost getItemAcceptanceRankBoost() {
        return RankBoost.SLIGHTLY_PREFERRED;
    }

    @Override
    public boolean setItem(
            int index,
            MCTownItem item
    ) {
        return false;
    }
}
