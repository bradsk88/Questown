package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WarpCycleBudgetTest {

    /** Drives the budget the way PostDowntimeWarper does: credit each step, start when allowed. */
    private static int cyclesIn(long totalTicks, long stepTicks, long cycleDuration) {
        WarpCycleBudget b = new WarpCycleBudget();
        int cycles = 0;
        for (long t = 0; t < totalTicks; t += stepTicks) {
            b.credit(Math.min(stepTicks, totalTicks - t));
            if (b.canStart(cycleDuration)) {
                b.spend(cycleDuration);
                cycles++;
            }
        }
        return cycles;
    }

    @Test
    void oneProductiveDay_ofTwoThousandTickHunts_allowsSix() {
        // hunter/sword: 2000-tick timed state, 5 sub-steps per window (400-tick spacing).
        Assertions.assertEquals(6, cyclesIn(12_000, 400, 2_000));
    }

    @Test
    void longerCycle_spendsMoreOfTheDay() {
        // hunter/sword_full_day: 6000 ticks; stepping on the shorter tier's spacing must not
        // let it run more often.
        Assertions.assertEquals(2, cyclesIn(12_000, 400, 6_000));
    }

    @Test
    void noCycleBeforeItsDurationHasElapsed() {
        WarpCycleBudget b = new WarpCycleBudget();
        b.credit(1_999);
        Assertions.assertFalse(b.canStart(2_000));
        b.credit(1);
        Assertions.assertTrue(b.canStart(2_000));
    }

    @Test
    void untimedJob_isNeverGated() {
        WarpCycleBudget b = new WarpCycleBudget();
        Assertions.assertTrue(b.canStart(0));
        b.spend(0);
        Assertions.assertTrue(b.canStart(0));
    }

    @Test
    void leftoverTime_carriesToTheNextCycle() {
        WarpCycleBudget b = new WarpCycleBudget();
        b.credit(3_000);
        b.spend(2_000);
        b.credit(1_000);
        Assertions.assertTrue(b.canStart(2_000));
    }
}
