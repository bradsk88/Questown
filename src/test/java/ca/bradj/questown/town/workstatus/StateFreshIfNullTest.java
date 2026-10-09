package ca.bradj.questown.town.workstatus;

import ca.bradj.questown.town.workstatus.State;
import java.util.function.Function;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The village/self_sufficiency gatherer flake (worlds 807/08/09 pass, 810/811 stall) resolves to the
 * supply-grab path dropping a job block whose per-owner work status is not yet seeded.
 *
 * <p>The gatherer's job-block gate is a deterministic block-ID check, so it is never the 50/50
 * discriminator. The discriminator is the room set used to decide what a leaver can grab: when that
 * leaver's per-owner ledger has no state for the welcome mat, the grab path used to do
 *
 * <pre>{@code State s = getJobBlockState.apply(bp); if (s == null) return false; }</pre>
 *
 * (skip the block) - so the mat landed in no room, {@code cleanRooms()} came back empty, and the leaver
 * was rejected with {@code shouldTakeItem REJECT needed-empty}, stuck in COLLECTING_SUPPLIES. 811's own
 * probes show exactly this ({@code cleanRooms EMPTY} while a different leaver sees {@code inner=[0=1]}).
 *
 * <p>The fix treats an unseeded block as FRESH in state 0 (matching the gate path at
 * {@code JobsClean.rooms}), so the mat is always a valid grab target for a first supply. This test pins
 * both the RED (the skip-null decision that emptied the 811 room set) and the GREEN (unseeded == fresh,
 * usable).
 */
class StateFreshIfNullTest {

    private static final String MAT = "welcome_mat_block@-13,64,10";

    @Test
    void unseeded_jobBlock_mustBeFresh_usableForFirstGrab() {
        // The leaver's per-owner store has never seeded this mat -> null lookup.
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
        // Before the fix the grab path was: if (getJobBlockState.apply(bp) == null) return false;
        // i.e. an unseeded mat was dropped, leaving the room set empty -> needed-empty. With the fix,
        // the unseeded mat resolves to a fresh (usable) state, so it is NO LONGER skipped.
        boolean stillSkipped = State.freshIfNull(pos -> null, MAT) == null;

        Assertions.assertFalse(stillSkipped, "with the fix, an unseeded mat is usable (fresh), not dropped - the 811 bug");
    }
}
