package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 2026-10-22 r5 playtest: once the town ran out of villager food, every townie looped
 * dining_no_table -> "gave up waiting for ingredients" -> the board -> dining_no_table, and nobody
 * worked, with raw meat in storage. The board must send a starving townie that just gave up on a
 * diner to the next stage, not the same diner.
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
     * 2026-10-23 r10: a starving hunter (fullness -272%) looped dining_no_table -> downtime ->
     * dining_no_table every ~200 ticks with raw beef in storage. The board's "ready for downtime"
     * gate ran before the stage-down, and downtime's instant-eat restarted the chain at the first
     * diner, so raw food was never reached.
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
