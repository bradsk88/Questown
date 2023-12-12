package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.town.WorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class JobBlock {

    public static @Nullable Integer getState(
            WorkStatusHandle<BlockPos, ItemStack> sl,
            BlockPos bp
    ) {
        WorkStatusStore.State oldState = sl.getJobBlockState(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
    }

    public static WorkStatusStore.State applyWork(
            WorkStatusHandle<BlockPos, ItemStack> sl,
            BlockPos bp,
            int nextWork,
            int nextTicks
    ) {
        WorkStatusStore.State oldState = sl.getJobBlockState(bp);
        int workLeft = oldState.workLeft();
        WorkStatusStore.State bs;
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
        if (bs.workLeft() == 0) {
            bs = bs.setProcessing(bs.processingState() + 1).setWorkLeft(nextWork).setCount(0);
        }
        if (nextTicks <= 0) {
            sl.setJobBlockState(bp, bs);
        } else {
            sl.setJobBlockStateWithTimer(bp, bs, nextTicks);
        }
        return bs;
    }

    private static WorkStatusStore.State reduceWorkLeft(WorkStatusStore.State oldState) {
        int l = oldState.workLeft();
        int newVal = l - 1;
        QT.BLOCK_LOGGER.debug("Setting work_left to {}", newVal);
        return oldState.setWorkLeft(newVal);
    }

    private static WorkStatusStore.State setProcessingState(
            WorkStatusStore.State oldState,
            int s
    ) {
        WorkStatusStore.State newState = oldState.setProcessing(s);
        QT.BLOCK_LOGGER.debug("Processing state set to {}", s);
        return newState;
    }

    public static @Nullable WorkStatusStore.State extractRawProduct(
            ServerLevel sl,
            WorkStatusHandle<BlockPos, ItemStack> jh,
            BlockPos block,
            Iterable<ItemStack> is,
            @Nullable TakeFn takeFn
    ) {
        WorkStatusStore.State oldState = jh.getJobBlockState(block);
        WorkStatusStore.State bs = oldState.setProcessing(0);
        for (ItemStack i : is) {
            // TODO[ASAP]: Don't drop items for gatherers - just skip them
            releaseOreFromBlock(sl, jh, block, bs, i, takeFn);
        }
        jh.setJobBlockState(block, bs);
        if (oldState.equals(bs)) {
            return null;
        }
        return bs;
    }

    private static void releaseOreFromBlock(
            ServerLevel sl,
            WorkStatusHandle<BlockPos, ItemStack> level,
            BlockPos b,
            WorkStatusStore.State currentState,
            ItemStack is,
            @Nullable TakeFn takeFn
    ) {
        Jobs.getOrCreateItemFromBlock(sl, b, takeFn, is);
        level.setJobBlockState(b, currentState.setProcessing(0));
        QT.BLOCK_LOGGER.debug("Moved item from block to world: {} at {}", is, b);
    }
}
