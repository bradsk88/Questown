package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WarpCycleBudgetTest {

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
        Assertions.assertEquals(6, cyclesIn(12_000, 400, 2_000));
    }

    @Test
    void longerCycle_spendsMoreOfTheDay() {
        // A longer job stepped on a shorter job's spacing doesn't run more often.
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
