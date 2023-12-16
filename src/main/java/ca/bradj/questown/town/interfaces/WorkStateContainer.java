package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.WorkStatusStore;
import org.jetbrains.annotations.Nullable;

public interface WorkStateContainer<POS> {

    @Nullable WorkStatusStore.State getJobBlockState(POS bp);

    void setJobBlockState(
            POS bp,
            WorkStatusStore.State bs
    );

    void setJobBlockStateWithTimer(
            POS bp,
            WorkStatusStore.State bs,
            int ticksToNextState
    );

    void clearState(POS bp);
}
