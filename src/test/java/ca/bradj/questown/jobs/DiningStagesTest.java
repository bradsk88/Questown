package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The board sends a starving townie that just gave up on a diner to the next stage, not the same
 * diner.
 */
class DiningStagesTest {

    private static final String ROOT = "hunter";

    @Test
    void fromWork_startsTheTableDiner_whenTheTownHasADiningRoom() {
        Assertions.assertEquals(new JobID(ROOT, "dining"), DiningStages.next(new JobID(ROOT, "sword"), true));
    }

    @Test
    void fromWork_startsTheNoTableDiner_withoutADiningRoom() {
        Assertions.assertEquals(new JobID(ROOT, "dining_no_table"), DiningStages.next(new JobID(ROOT, "sword"), false));
    }

    @Test
    void stillStarvingAfterTheTable_triesWithoutATable() {
        Assertions.assertEquals(new JobID(ROOT, "dining_no_table"), DiningStages.next(new JobID(ROOT, "dining"), true));
    }

    @Test
    void stillStarvingAfterNoTable_triesRawFood() {
        Assertions.assertEquals(new JobID(ROOT, "dining_raw_food"), DiningStages.next(new JobID(ROOT, "dining_no_table"), false));
    }

    @Test
    void stillStarvingAfterRawFood_stopsTryingToEat() {
        Assertions.assertNull(DiningStages.next(new JobID(ROOT, "dining_raw_food"), false));
    }

    @Test
    void theThreeDinersAreDiners_workIsNot() {
        Assertions.assertTrue(DiningStages.isDiner(new JobID(ROOT, "dining")));
        Assertions.assertTrue(DiningStages.isDiner(new JobID(ROOT, "dining_no_table")));
        Assertions.assertTrue(DiningStages.isDiner(new JobID(ROOT, "dining_raw_food")));
        Assertions.assertFalse(DiningStages.isDiner(new JobID(ROOT, "sword")));
        Assertions.assertFalse(DiningStages.isDiner(new JobID(ROOT, "downtime")));
    }

    /**
     * The stage-down must run before the board's "ready for downtime" gate: downtime's instant-eat
     * would restart the chain at the first diner, so raw food would never be reached.
     */
    @Test
    void downtimeMustNotInterruptAStarvingDiner() {
        Assertions.assertFalse(DiningStages.downtimeMayInterrupt(new JobID(ROOT, "dining_no_table"), true));
        Assertions.assertFalse(DiningStages.downtimeMayInterrupt(new JobID(ROOT, "dining"), true));
    }

    @Test
    void downtimeMayInterruptWork_orADinerThatAte() {
        Assertions.assertTrue(DiningStages.downtimeMayInterrupt(new JobID(ROOT, "sword"), true));
        Assertions.assertTrue(DiningStages.downtimeMayInterrupt(new JobID(ROOT, "dining_no_table"), false));
    }
}
