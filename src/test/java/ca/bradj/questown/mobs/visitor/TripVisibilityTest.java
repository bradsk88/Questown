package ca.bradj.questown.mobs.visitor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TripVisibilityTest {

    /**
     * The status sequence a hunter logged leaving town (autotest hunter/sword, 2026-10-29):
     * state:1 (hidden), DROPPING_LOOT for one tick (allowed), WAITING_FOR_TIMED_STATE (hidden).
     */
    @Test
    void oneAllowedTickBetweenHiddenStates_staysHidden() {
        TripVisibility tv = new TripVisibility();
        boolean vis = tv.next(true, false);
        Assertions.assertFalse(vis);
        vis = tv.next(vis, true);
        Assertions.assertFalse(vis, "the one-tick DROPPING_LOOT must not show the villager");
        vis = tv.next(vis, false);
        Assertions.assertFalse(vis);
    }

    @Test
    void returningVillager_showsOnceAllowedTwoTicksRunning() {
        TripVisibility tv = new TripVisibility();
        boolean vis = tv.next(true, false);
        vis = tv.next(vis, true);
        Assertions.assertFalse(vis);
        vis = tv.next(vis, true);
        Assertions.assertTrue(vis);
        vis = tv.next(vis, true);
        Assertions.assertTrue(vis);
    }

    @Test
    void hidingIsImmediate() {
        TripVisibility tv = new TripVisibility();
        Assertions.assertTrue(tv.next(true, true));
        Assertions.assertFalse(tv.next(true, false));
    }
}
