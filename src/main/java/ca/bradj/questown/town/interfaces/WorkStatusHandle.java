package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.WorkStatusStore;
import org.jetbrains.annotations.Nullable;

public interface WorkStatusHandle<POS, ITEM> {

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

    boolean tryInsertItem(
            WorkStatusStore.InsertionRules<ITEM> rules,
            ITEM item,
            POS bp,
            int workToNextStep,
            int timeToNextStep
    );

    boolean canInsertItem(
            ITEM item,
            POS bp
    );

    @Nullable Integer getTimeToNextState(POS bp);
}
