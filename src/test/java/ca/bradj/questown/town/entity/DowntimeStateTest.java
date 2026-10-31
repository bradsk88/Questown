package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pure tests of the {@link DowntimeState} state machine, independent of a handle or a level.
 * Covers the "rest is a reward, not a default" rule (a fresh/never-worked townie works before it can
 * rest) plus the no-work fallback (a rested townie with nothing to do rests rather than idling forever).
 */
class DowntimeStateTest {

    private static final long N = 100L;

    @Test
    void fresh_isNotReadyForRest() {
        // A townie that has not worked and has not rested: work first (fixes the run-19 soft-lock).
        DowntimeState fresh = DowntimeState.fresh(N);
        Assertions.assertFalse(fresh.isReadyForRest(1L));
        Assertions.assertFalse(fresh.isReadyForRest(Long.MAX_VALUE / 2));
        // Not rested at all -> no last-rest anchor, so the idle fallback is also off.
        Assertions.assertFalse(fresh.isReadyForIdleRest(1L));
    }

    @Test
    void restedButNoWork_isNotReadyForRest_butIsReadyForIdleRest() {
        // They rested (at 0) but never worked. Primary: not earned. Escape: ready once the rest is
        // old enough - so a no-work townie rests instead of sitting idle forever.
        DowntimeState s = DowntimeState.fresh(N).rest(0L);
        Assertions.assertFalse(s.isReadyForRest(N + 1L), "rested but never worked: not earned");
        Assertions.assertFalse(s.isReadyForIdleRest(N - 1L), "just rested: not yet");
        Assertions.assertTrue(s.isReadyForIdleRest(N), "rested long enough + no work: rest");
    }

    @Test
    void workedBeforeThisRest_isNotEarnedYethatTheyWorkedAgain() {
        // Rested at 10, but they last worked at 5 (before this rest). They must work again after 10.
        DowntimeState s = DowntimeState.fresh(N).rest(10L).work(5L);
        Assertions.assertFalse(s.isReadyForRest(10L + N + 1L), "worked only before this rest: not earned");
    }

    @Test
    void workedSinceLastRest_isReadyAfterRestIsOldEnough() {
        // Rested at 0, then worked at 90 (after the rest). Earned a rest; ready once the rest is old.
        DowntimeState s = DowntimeState.fresh(N).rest(0L).work(90L);
        Assertions.assertFalse(s.isReadyForRest(N - 1L), "not enough time since the last rest yet");
        Assertions.assertTrue(s.isReadyForRest(N), "worked since the rest + rest old enough: ready");
        // The escape is a superset: once ready for a rest, also ready for an idle rest.
        Assertions.assertTrue(s.isReadyForIdleRest(N));
    }

    @Test
    void restedAfterWorkThenIdle_isReadyForIdleRestNotRest() {
        // Rested at 0, worked at 90, then rested again at 200 (the NOON/day-change path). That reset the
        // "earn a rest" clock: they must work again after 200 before the primary fires, though the idle
        // fallback is available once the 200 rest is old enough.
        DowntimeState s = DowntimeState.fresh(N).rest(0L).work(90L).rest(200L);
        Assertions.assertFalse(s.isReadyForRest(200L + N), "rested at 200 wiped the 90 work: not earned");
        Assertions.assertTrue(s.isReadyForIdleRest(200L + N), "no work + rested long enough: rest");
    }

    @Test
    void transitionsAreValueBased_andOldStateUnchanged() {
        DowntimeState fresh = DowntimeState.fresh(N);
        DowntimeState rested = fresh.rest(42L);
        DowntimeState worked = rested.work(100L);
        Assertions.assertEquals(42L, (long) rested.lastRest());
        Assertions.assertNull(fresh.lastRest(), "the old state is untouched by the transition");
        Assertions.assertEquals(100L, (long) worked.lastWork());
        Assertions.assertEquals(42L, (long) worked.lastRest());
        Assertions.assertNotSame(fresh, rested);
    }
}
