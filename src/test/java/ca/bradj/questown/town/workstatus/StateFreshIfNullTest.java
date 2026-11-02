package ca.bradj.questown.town.workstatus;

import ca.bradj.questown.town.workstatus.State;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** A welcome mat this villager hasn't seeded counts as fresh state 0, so the villager still takes supplies for it. */
class StateFreshIfNullTest {

    private static final String MAT = "welcome_mat_block@-13,64,10";

    @Test
    void unseeded_jobBlock_mustBeFresh_usableForFirstGrab() {
        Function<String, State> unseeded = pos -> null;

        State s = State.freshIfNull(unseeded, MAT);

        Assertions.assertNotNull(s, "an unseeded job block must be treated as a usable fresh block, not skipped");
        Assertions.assertEquals(
                State.freshAtState(0),
                s,
                "unseeded means state-0 fresh - the first grab can happen; this is what 811 was missing"
        );
        Assertions.assertEquals(0, s.ingredientCount(), "a fresh block has no ingredients inserted yet");
        Assertions.assertFalse(s.hasWorkLeft(), "a fresh block has no work left to extract yet");
    }

    @Test
    void seeded_jobBlock_isPreservedNotOverwritten() {
        State seeded = State.freshAtState(1);
        Function<String, State> seededLookup = pos -> seeded;

        Assertions.assertSame(seeded, State.freshIfNull(seededLookup, MAT));
    }

    @Test
    void unseeded_mustNoLongerBeSkipped_asItWasIn811() {
        boolean stillSkipped = State.freshIfNull(pos -> null, MAT) == null;

        Assertions.assertFalse(stillSkipped, "with the fix, an unseeded mat is usable (fresh), not dropped - the 811 bug");
    }
}
