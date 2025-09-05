package ca.bradj.questown.blocks.entity;

import ca.bradj.questown.blocks.SeedBinBlock;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.integration.minecraft.MCContainerInterface;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.RankBoost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SeedBinBlockEntity extends BlockEntity implements MCContainerInterface {

    public static final Item INNER_ITEM = Items.WHEAT_SEEDS;

    public SeedBinBlockEntity(
            BlockPos p_155229_,
            BlockState p_155230_
    ) {
        super(TilesInit.SEED_BIN.get(), p_155229_, p_155230_);
    }

    @Override
    public int size() {
        return SeedBinBlock.getLevel(getBlockState());
    }

    @Override
    public MCTownItem getItem(int i) {
        if (i > SeedBinBlock.getLevel(getBlockState())) {
            return MCTownItem.Air();
        }
        return MCTownItem.fromMCItemStack(INNER_ITEM.getDefaultInstance());
    }

    @Override
    public MCTownItem removeItem(int index) {
        MCTownItem item = getItem(index);
        if (!item.isEmpty()) {
            if (getLevel() instanceof ServerLevel sl) {
                BlockState bs = getBlockState();
                int curLevel = SeedBinBlock.getLevel(bs);
                if (curLevel == 0) {
                    return MCTownItem.Air();
                }
                sl.setBlockAndUpdate(getBlockPos(), SeedBinBlock.reduceLevel(bs));
            }
        }
        return item;
    }

    @Override
    public boolean isFull() {
        return SeedBinBlock.isFull(getBlockState());
    }

    @Override
    public String toShortString() {
        return "SeedBin["+SeedBinBlock.getLevel(getBlockState())+"]";
    }

    @Override
    public String toShortString(boolean includeAir) {
        return toShortString();
    }

    @Override
    public boolean canAcceptIfSpaceAllows(MCTownItem item) {
        return item.get().equals(INNER_ITEM);
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
        return SeedBinBlock.addSeed(level, getBlockPos(), getBlockState());
    }
}
