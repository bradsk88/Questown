package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("DataFlowIssue")
class NeedsRegistrationsTest {

    public static final Position ARB_POS = new Position(0, 0);

    @Test
    void addUnmetShouldRegisterFirstIngredientIfStateIsEmpty() {
        AtomicInteger unmetIngredient = new AtomicInteger();
        NeedsRegistrations<Position, Void> r = new NeedsRegistrations<Position, Void>(
                (no, idx) -> {
                    if (idx.isTool()) {
                        throw new AssertionError("Should not set tool");
                    }
                    unmetIngredient.set(idx.ingredientIndex());

                },
                (no, pos) -> null
        );
        r.addUnmet(null, ARB_POS, false);
        assertEquals(0, unmetIngredient.get());
    }

    @Test
    void addUnmetShouldRegisterFirstIngredientIfStateIsZero() {
        AtomicInteger unmetIngredient = new AtomicInteger();
        NeedsRegistrations<Position, Void> r = new NeedsRegistrations<Position, Void>(
                (no, idx) -> {
                    if (idx.isTool()) {
                        throw new AssertionError("Should not set tool");
                    }
                    unmetIngredient.set(idx.ingredientIndex());

                },
                (no, pos) -> State.fresh()
        );
        r.addUnmet(null, ARB_POS, false);
        assertEquals(0, unmetIngredient.get());
    }

    @Test
    void addUnmetShouldRegisterFirstToolIfStateIsZeroWithWorkRemaining() {
        AtomicInteger unmetTool = new AtomicInteger();
        NeedsRegistrations<Position, Void> r = new NeedsRegistrations<Position, Void>(
                (no, idx) -> {
                    if (idx.isTool()) {
                        unmetTool.set(idx.toolIndex());
                        return;
                    }
                    throw new AssertionError("Should not set ingredient");

                },
                (no, pos) -> State.fresh().setWorkLeft(1)
        );
        r.addUnmet(null, ARB_POS, false);
        assertEquals(0, unmetTool.get());
    }

    @Test
    void addUnmetShouldRegisterSecondIngredientIfStateIsOne() {
        AtomicInteger unmetIngredient = new AtomicInteger();
        NeedsRegistrations<Position, Void> r = new NeedsRegistrations<Position, Void>(
                (no, idx) -> {
                    if (idx.isTool()) {
                        throw new AssertionError("Should not set tool");
                    }
                    unmetIngredient.set(idx.ingredientIndex());

                },
                (no, pos) -> State.freshAtState(1)
        );
        r.addUnmet(null, ARB_POS, false);
        assertEquals(1, unmetIngredient.get());
    }

    @Test
    void addUnmetShouldRegisterSecondToolIfStateIsOneAndWorkRemains() {
        AtomicInteger unmetTool = new AtomicInteger();
        NeedsRegistrations<Position, Void> r = new NeedsRegistrations<Position, Void>(
                (no, idx) -> {
                    if (idx.isTool()) {
                        unmetTool.set(idx.toolIndex());
                        return;
                    }
                    throw new AssertionError("Should not set ingredient");

                },
                (no, pos) -> State.freshAtState(1).setWorkLeft(1)
        );
        r.addUnmet(null, ARB_POS, false);
        assertEquals(1, unmetTool.get());
    }

    @Test
    void addUnmetShouldRegisterFirstIngredientIfWorkSpotIsNUll() {
        AtomicInteger unmetIngr = new AtomicInteger();
        NeedsRegistrations<Position, Void> r = new NeedsRegistrations<Position, Void>(
                (no, idx) -> {
                    if (idx.isTool()) {
                        throw new AssertionError("Should not set tool");
                    }
                    unmetIngr.set(idx.ingredientIndex());

                },
                (no, pos) -> State.freshAtState(1).setWorkLeft(1)
        );
        r.addUnmet(null, null, false);
        assertEquals(0, unmetIngr.get());
    }
}