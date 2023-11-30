package ca.bradj.questown.blocks;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.town.TownWorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

public class JobBlock {

    public static final IntegerProperty PROCESSING_STATE = IntegerProperty.create(
            "processing_state", 0, 4
    );

    public static final IntegerProperty WORK_LEFT = IntegerProperty.create(
            "work_left", 0, Config.SMELTER_WORK_REQUIRED.get()
    );

    public static @Nullable Integer getState(
            WorkStatusHandle sl,
            BlockPos bp
    ) {
        TownWorkStatusStore.State oldState = sl.getJobBlockState(bp);
        if (oldState == null) {
            return null;
        }
        return oldState.processingState();
    }

    public static TownWorkStatusStore.State applyWork(
            WorkStatusHandle sl,
            BlockPos bp,
            int nextWork
    ) {
        TownWorkStatusStore.State oldState = sl.getJobBlockState(bp);
        int workLeft = oldState.workLeft();
        TownWorkStatusStore.State bs;
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
        sl.setJobBlockState(bp, bs);
        return bs;
    }

    private static TownWorkStatusStore.State reduceWorkLeft(TownWorkStatusStore.State oldState) {
        int l = oldState.workLeft();
        int newVal = l - 1;
        QT.BLOCK_LOGGER.debug("Setting work_left to {}", newVal);
        return oldState.setWorkLeft(newVal);
    }

    private static TownWorkStatusStore.State setProcessingState(
            TownWorkStatusStore.State oldState,
            int s
    ) {
        TownWorkStatusStore.State newState = oldState.setProcessing(s);
        QT.BLOCK_LOGGER.debug("Processing state set to {}", s);
        return newState;
    }

    public static @Nullable TownWorkStatusStore.State extractRawProduct(
            ServerLevel sl,
            WorkStatusHandle jh,
            BlockPos block,
            Iterable<ItemStack> is,
            @Nullable TakeFn takeFn
    ) {
        TownWorkStatusStore.State oldState = jh.getJobBlockState(block);
        TownWorkStatusStore.State bs = oldState.setProcessing(0);
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
            WorkStatusHandle level,
            BlockPos b,
            TownWorkStatusStore.State currentState,
            ItemStack is,
            @Nullable TakeFn takeFn
    ) {
        Jobs.getOrCreateItemFromBlock(sl, b, takeFn, is);
        level.setJobBlockState(b, currentState.setProcessing(0));
        QT.BLOCK_LOGGER.debug("Moved item from block to world: {} at {}", is, b);
    }
}
