package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

public interface ImmutableWorkStateContainer<POS, SELF> {

    @Nullable AbstractWorkStatusStore.State getJobBlockState(POS bp);

    ImmutableMap<POS, AbstractWorkStatusStore.State> getAll();

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
