package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.WorkStatusStore;
import org.jetbrains.annotations.Nullable;

public interface WorkStatusHandle<POS, ITEM> extends WorkStateContainer<POS> {

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
