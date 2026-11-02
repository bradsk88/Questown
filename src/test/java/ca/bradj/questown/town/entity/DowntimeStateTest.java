package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** {@link DowntimeState}: a villager works before it can rest, and with nothing to do it rests rather than idling. */
class DowntimeStateTest {

    private static final long N = 100L;

    @Test
    void fresh_isNotReadyForRest() {
        // Never worked or rested: work first.
        DowntimeState fresh = DowntimeState.fresh(N);
        Assertions.assertFalse(fresh.isReadyForRest(1L));
        Assertions.assertFalse(fresh.isReadyForRest(Long.MAX_VALUE / 2));
        Assertions.assertFalse(fresh.isReadyForIdleRest(1L));
    }

    @Test
    void restedButNoWork_isNotReadyForRest_butIsReadyForIdleRest() {
        // Rested but never worked: not earned, but with nothing to do it rests once the rest is old.
        DowntimeState s = DowntimeState.fresh(N).rest(0L);
        Assertions.assertFalse(s.isReadyForRest(N + 1L), "rested but never worked: not earned");
        Assertions.assertFalse(s.isReadyForIdleRest(N - 1L), "just rested: not yet");
        Assertions.assertTrue(s.isReadyForIdleRest(N), "rested long enough + no work: rest");
    }

    @Test
    void workedBeforeThisRest_isNotEarnedYethatTheyWorkedAgain() {
        // Worked before the last rest: must work again.
        DowntimeState s = DowntimeState.fresh(N).rest(10L).work(5L);
        Assertions.assertFalse(s.isReadyForRest(10L + N + 1L), "worked only before this rest: not earned");
    }

    @Test
    void workedSinceLastRest_isReadyAfterRestIsOldEnough() {
        // Worked after the last rest: ready once the rest is old.
        DowntimeState s = DowntimeState.fresh(N).rest(0L).work(90L);
        Assertions.assertFalse(s.isReadyForRest(N - 1L), "not enough time since the last rest yet");
        Assertions.assertTrue(s.isReadyForRest(N), "worked since the rest + rest old enough: ready");
        Assertions.assertTrue(s.isReadyForIdleRest(N));
    }

    @Test
    void restedAfterWorkThenIdle_isReadyForIdleRestNotRest() {
        // A new rest starts the count again.
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
