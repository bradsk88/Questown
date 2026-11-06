package ca.bradj.questown.town.workstatus;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

class StateTest {

    private static final String MAT = "welcome_mat_block@-13,64,10";

    @Test
    void seededOrFresh_unseededBlock_isFreshStateZero_soTheVillagerCanTakeItsFirstSupplies() {
        Function<String, State> unseeded = pos -> null;

        State s = State.seededOrFresh(unseeded, MAT);

        Assertions.assertEquals(State.freshAtState(0), s);
        Assertions.assertEquals(0, s.ingredientCount());
        Assertions.assertFalse(s.hasWorkLeft());
    }

    @Test
    void seededOrFresh_seededBlock_keepsItsState() {
        State seeded = State.freshAtState(1);
        Function<String, State> seededLookup = pos -> seeded;

        Assertions.assertSame(seeded, State.seededOrFresh(seededLookup, MAT));
    }
}
