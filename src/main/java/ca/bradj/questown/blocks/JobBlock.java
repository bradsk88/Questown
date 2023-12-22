package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class JobBlock {

    public static @Nullable Integer getState(
            Function<BlockPos, AbstractWorkStatusStore.State> sl,
            BlockPos bp
    ) {
        AbstractWorkStatusStore.State oldState = sl.apply(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
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
            ImmutableWorkStateContainer<BlockPos, ?> jh,
            BlockPos block,
            Iterable<MCHeldItem> is,
            @Nullable TakeFn takeFn,
            boolean nullifyExcess
    ) {
        AbstractWorkStatusStore.State oldState = jh.getJobBlockState(block);
        AbstractWorkStatusStore.State bs = oldState.setProcessing(0);
        for (MCHeldItem i : is) {
            releaseOreFromBlock(sl, jh, block, bs, i, takeFn, nullifyExcess);
        }
        jh.setJobBlockState(block, bs);
        if (oldState.equals(bs)) {
            return null;
        }
        return bs;
    }

    private static void releaseOreFromBlock(
            ServerLevel sl,
            ImmutableWorkStateContainer<BlockPos, ?> level,
            BlockPos b,
            AbstractWorkStatusStore.State currentState,
            MCHeldItem is,
            @Nullable TakeFn takeFn,
            boolean discardExcess
    ) {
        Jobs.getOrCreateItemFromBlock(sl, b, takeFn, is, discardExcess);
        level.setJobBlockState(b, currentState.setProcessing(0));
        QT.BLOCK_LOGGER.debug("Moved item from block to world: {} at {}", is, b);
    }
}
