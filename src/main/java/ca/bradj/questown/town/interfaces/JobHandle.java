package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.TownJobHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface JobHandle {

    @Nullable TownJobHandle.State getJobBlockState(BlockPos bp);

    void setJobBlockState(
            BlockPos bp,
            TownJobHandle.State bs
    );

    boolean tryInsertItem(
            TownJobHandle.InsertionRules rules,
            ItemStack item,
            BlockPos bp,
            int workToNextStep
    );

    boolean canInsertItem(
            ItemStack item,
            BlockPos bp
    );
}
