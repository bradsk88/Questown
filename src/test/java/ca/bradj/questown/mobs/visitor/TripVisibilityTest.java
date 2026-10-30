package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
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
