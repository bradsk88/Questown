package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.town.TownJobHandle;
import ca.bradj.questown.town.interfaces.JobHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class JobBlock {

    public static @Nullable Integer getState(
            JobHandle sl,
            BlockPos bp
    ) {
        TownJobHandle.State oldState = sl.getJobBlockState(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
    }

    public static TownJobHandle.State applyWork(
            JobHandle sl,
            BlockPos bp
    ) {
        TownJobHandle.State oldState = sl.getJobBlockState(bp);
        int workLeft = oldState.workLeft();
        TownJobHandle.State bs;
        if (workLeft <= 0) {
            Integer state = getState(sl, bp);
            if (state == null) {
                state = 0;
            }
            bs = setProcessingState(oldState, state + 1);
        } else {
            bs = reduceWorkLeft(oldState);
        }
        if (oldState.equals(bs)) {
            return null;
        }
        sl.setJobBlockState(bp, bs);
        return bs;
    }

    private static TownJobHandle.State reduceWorkLeft(TownJobHandle.State oldState) {
        int l = oldState.workLeft();
        int newVal = l - 1;
        QT.BLOCK_LOGGER.debug("Setting work_left to {}", newVal);
        return oldState.setWorkLeft(newVal);
    }

    private static TownJobHandle.State setProcessingState(
            TownJobHandle.State oldState,
            int s
    ) {
        TownJobHandle.State newState = oldState.setProcessing(s);
        QT.BLOCK_LOGGER.debug("Processing state set to {}", s);
        return newState;
    }

    public static @Nullable TownJobHandle.State extractRawProduct(
            ServerLevel sl,
            JobHandle jh,
            BlockPos block,
            Supplier<ItemStack> is,
            @Nullable TakeFn takeFn
    ) {
        TownJobHandle.State oldState = jh.getJobBlockState(block);
        TownJobHandle.State bs = oldState.setProcessing(0);
        moveOreToWorld(sl, jh, block, bs, is.get(), takeFn);
        jh.setJobBlockState(block, bs);
        if (oldState.equals(bs)) {
            return null;
        }
        return bs;
    }

    private static void moveOreToWorld(
            ServerLevel sl,
            JobHandle level,
            BlockPos b,
            TownJobHandle.State currentState,
            ItemStack is,
            @Nullable TakeFn takeFn
    ) {
        Jobs.getOrCreateItemFromBlock(sl, b, takeFn, is);
        level.setJobBlockState(b, currentState.setProcessing(0));
        QT.BLOCK_LOGGER.debug("Moved item from block to world: {} at {}", is, b);
    }
}
