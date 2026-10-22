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
}
