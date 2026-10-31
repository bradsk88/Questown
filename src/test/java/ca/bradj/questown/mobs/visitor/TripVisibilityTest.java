package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TripVisibilityTest {

    private static boolean hideFully(TripVisibility tv, boolean vis) {
        for (int i = 0; i < TripVisibility.HIDE_AFTER_TICKS; i++) {
            vis = tv.next(vis, false);
        }
        return vis;
    }

    /**
     * The status sequence a hunter logged leaving town (autotest hunter/sword, 2026-10-29):
     * state:1 (hidden), DROPPING_LOOT for one tick (allowed), WAITING_FOR_TIMED_STATE (hidden).
     */
    @Test
    void oneAllowedTickBetweenHiddenStates_staysHidden() {
        TripVisibility tv = new TripVisibility();
        boolean vis = hideFully(tv, true);
        Assertions.assertFalse(vis);
        vis = tv.next(vis, true);
        Assertions.assertFalse(vis, "the one-tick DROPPING_LOOT must not show the villager");
        vis = tv.next(vis, false);
        Assertions.assertFalse(vis);
    }

    /** The same one-tick gap while the leaving villager is still drawn: no flicker, it hides once. */
    @Test
    void oneAllowedTickWhileLeaving_neverFlickers() {
        TripVisibility tv = new TripVisibility();
        boolean vis = tv.next(true, false);
        Assertions.assertTrue(vis);
        vis = tv.next(vis, true);
        Assertions.assertTrue(vis);
        vis = hideFully(tv, vis);
        Assertions.assertFalse(vis);
    }

    @Test
    void returningVillager_showsOnceAllowedTwoTicksRunning() {
        TripVisibility tv = new TripVisibility();
        boolean vis = hideFully(tv, true);
        vis = tv.next(vis, true);
        Assertions.assertFalse(vis);
        vis = tv.next(vis, true);
        Assertions.assertTrue(vis);
        vis = tv.next(vis, true);
        Assertions.assertTrue(vis);
    }

    /** Dev playtest 2026-10-30: villagers vanished before reaching the work spot; hide ~1 s later. */
    @Test
    void hidingWaitsASecond() {
        TripVisibility tv = new TripVisibility();
        boolean vis = true;
        for (int i = 1; i < TripVisibility.HIDE_AFTER_TICKS; i++) {
            vis = tv.next(vis, false);
            Assertions.assertTrue(vis, "hidden after " + i + " ticks");
        }
        Assertions.assertFalse(tv.next(vis, false));
    }

    @Test
    void leaving_sparklesAboveTheWorkSpot() {
        Vec3 at = TripVisibility.sparkleAt(false, new Vec3(81.5, 64, -5.5), new BlockPos(80, 64, -6));
        Assertions.assertEquals(new Vec3(80.5, 65, -5.5), at);
    }

    @Test
    void returning_orNoWorkSpot_sparklesAtTheVillager() {
        Vec3 villager = new Vec3(81.5, 64, -5.5);
        Assertions.assertEquals(villager, TripVisibility.sparkleAt(true, villager, new BlockPos(80, 64, -6)));
        Assertions.assertEquals(villager, TripVisibility.sparkleAt(false, villager, null));
    }
}
