package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.town.workstatus.State;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class NeedsRegistrations<POS, EXTRA> {
    private final BiConsumer<EXTRA, Need> registerUnmetNeed;
    private final BiFunction<EXTRA, POS, State> getState;

    public record Need(
            @Nullable Integer ingredientIndex,
            @Nullable Integer toolIndex
    ) {
        public boolean isTool() {
            return toolIndex != null;
        }
    }

    public NeedsRegistrations(
            BiConsumer<EXTRA, Need> registerUnmetNeed,
            BiFunction<EXTRA, POS, @Nullable State> getState
    ) {
        this.registerUnmetNeed = registerUnmetNeed;
        this.getState = getState;
    }

    public void addUnmet(
            EXTRA extra,
            @Nullable POS workspot
    ) {
        State state = State.fresh();
        if (workspot != null) {
            state = getState.apply(extra, workspot);
        }
        if (state == null) {
            registerUnmetNeed.accept(extra, new Need(0, null));
            return;
        }
        if (state.hasWorkLeft()) {
            registerUnmetNeed.accept(extra, new Need(null, state.processingState()));
            return;
        }
        registerUnmetNeed.accept(extra, new Need(state.processingState(), null));
    }
}
