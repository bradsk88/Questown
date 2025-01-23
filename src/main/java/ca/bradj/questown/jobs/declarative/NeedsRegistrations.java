package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.town.workstatus.State;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class NeedsRegistrations<POS, EXTRA> {
    private final BiConsumer<EXTRA, Need> registerUnmetNeed;
    private final BiFunction<EXTRA, POS, State> getState;
    private final Consumer<EXTRA> registerUnmetRoom;

    public record Need(
            @Nullable Integer ingredientIndex,
            @Nullable Integer toolIndex
    ) {

        public boolean isTool() {
            return toolIndex != null;
        }
    }

    public NeedsRegistrations(
            Consumer<EXTRA> registerUnmetRoom,
            BiConsumer<EXTRA, Need> registerUnmetNeed,
            BiFunction<EXTRA, POS, @Nullable State> getState
    ) {
        this.registerUnmetNeed = registerUnmetNeed;
        this.getState = getState;
        this.registerUnmetRoom = registerUnmetRoom;
    }

    public void addUnmet(
            EXTRA extra,
            @Nullable POS workspot,
            boolean hasInserted
    ) {
        State state = State.fresh();
        if (workspot != null) {
            state = getState.apply(extra, workspot);
        }
        if (state == null) {
            registerUnmetNeed.accept(extra, new Need(0, null));
            return;
        }
        int ingredientIndex = state.processingState();
        if (state.hasWorkLeft()) {
            registerUnmetNeed.accept(extra, new Need(null, ingredientIndex));
            return;
        }
        if (hasInserted) {
            ingredientIndex += 1;
        }
        registerUnmetNeed.accept(extra, new Need(ingredientIndex, null));
    }

    public void addUnmetRoom(
            EXTRA extra
    ) {
        registerUnmetRoom.accept(extra);
    }
}
