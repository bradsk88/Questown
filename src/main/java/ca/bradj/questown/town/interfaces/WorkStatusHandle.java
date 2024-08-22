package ca.bradj.questown.town.interfaces;

import org.jetbrains.annotations.Nullable;

public interface WorkStatusHandle<POS, ITEM> extends ImmutableWorkStateContainer<POS, Boolean> {

    boolean canInsertItem(
            ITEM item,
            POS bp
    );

    @Nullable Integer getTimeToNextState(POS bp);

    void clearAllTimers();
}
