package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.AbstractWorkStatusStore;

public interface ImmutableWorkStateContainer<POS, SELF> {
    SELF setJobBlockState(
            POS bp,
            AbstractWorkStatusStore.State bs
    );

    SELF setJobBlockStateWithTimer(
            POS bp,
            AbstractWorkStatusStore.State bs,
            int ticksToNextState
    );

    SELF clearState(POS bp);

}
