package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class JobBlock {

    public static @Nullable Integer getState(
            WorkStateContainer<BlockPos> sl,
            BlockPos bp
    ) {
        AbstractWorkStatusStore.State oldState = sl.getJobBlockState(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
    }

    public static AbstractWorkStatusStore.State applyWork(
            WorkStateContainer<BlockPos> sl,
            BlockPos bp,
            int nextWork,
            int nextTicks
    ) {
        AbstractWorkStatusStore.State oldState = sl.getJobBlockState(bp);
        int workLeft = oldState.workLeft();
        AbstractWorkStatusStore.State bs;
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

    private static AbstractWorkStatusStore.State reduceWorkLeft(AbstractWorkStatusStore.State oldState) {
        int l = oldState.workLeft();
        int newVal = l - 1;
        QT.BLOCK_LOGGER.debug("Setting work_left to {}", newVal);
        return oldState.setWorkLeft(newVal);
    }

    private static AbstractWorkStatusStore.State setProcessingState(
            AbstractWorkStatusStore.State oldState,
            int s
    ) {
        AbstractWorkStatusStore.State newState = oldState.setProcessing(s);
        QT.BLOCK_LOGGER.debug("Processing state set to {}", s);
        return newState;
    }

    public static @Nullable AbstractWorkStatusStore.State extractRawProduct(
            ServerLevel sl,
            WorkStateContainer<BlockPos> jh,
            BlockPos block,
            Iterable<MCHeldItem> is,
            @Nullable TakeFn takeFn
    ) {
        AbstractWorkStatusStore.State oldState = jh.getJobBlockState(block);
        AbstractWorkStatusStore.State bs = oldState.setProcessing(0);
        for (MCHeldItem i : is) {
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
            WorkStateContainer<BlockPos> level,
            BlockPos b,
            AbstractWorkStatusStore.State currentState,
            MCHeldItem is,
            @Nullable TakeFn takeFn
    ) {
        Jobs.getOrCreateItemFromBlock(sl, b, takeFn, is);
        level.setJobBlockState(b, currentState.setProcessing(0));
        QT.BLOCK_LOGGER.debug("Moved item from block to world: {} at {}", is, b);
    }
}
