package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import org.jetbrains.annotations.Nullable;

public interface WorkStateContainer<POS> {

    @Nullable AbstractWorkStatusStore.State getJobBlockState(POS bp);

    void setJobBlockState(
            POS bp,
            AbstractWorkStatusStore.State bs
    );

    void setJobBlockStateWithTimer(
            POS bp,
            AbstractWorkStatusStore.State bs,
            int ticksToNextState
    );

    void clearState(POS bp);
}
