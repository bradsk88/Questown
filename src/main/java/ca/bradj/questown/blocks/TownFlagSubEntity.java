package ca.bradj.questown.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;

public interface TownFlagSubEntity {

    Collection<ItemStack> dropWhenOrphaned(BlockPos flagPos);

    void addTickListener(Runnable listener);
}
