package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.TownWorkStatusStore;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface WorkStatusHandle {

    @Nullable TownWorkStatusStore.State getJobBlockState(BlockPos bp);

    void setJobBlockState(
            BlockPos bp,
            TownWorkStatusStore.State bs
    );

    boolean tryInsertItem(
            TownWorkStatusStore.InsertionRules rules,
            ItemStack item,
            BlockPos bp,
            int workToNextStep
    );

    boolean canInsertItem(
            ItemStack item,
            BlockPos bp
    );
}
