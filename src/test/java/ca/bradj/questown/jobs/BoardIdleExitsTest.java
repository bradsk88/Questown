package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * r17 #17 (2026-10-25): a gatherer deposited its Block of Progress, then sat in
 * gatherer:bop_depositing for minutes ("needs blocks of progress"): the board was asked 501
 * times and answered "stays: no branch applied". The hunter's deposit only left because it
 * happened to be ready for downtime. A finished deposit now relaxes like a fed diner.
 */
class BoardIdleExitsTest {

    @Test
    void aBopDepositorWithNothingElseToDo_isDone() {
        Assertions.assertTrue(BoardIdleExits.depositFinished(new JobID("gatherer", "bop_depositing")));
    }

    @Test
    void aWorkJob_isNot() {
        Assertions.assertFalse(BoardIdleExits.depositFinished(new JobID("gatherer", "gather")));
    }

    /**
     * r17 #10 (dev 2026-10-25): at dusk a hunter with swords and food in storage sat in
     * seeking_work showing "looking for supplies"; its trip did not fit the rest of the day. The
     * dev: "or just skip ahead to relaxing".
     */
    @Test
    void noWorkBecauseNothingFitsTheDay_relaxes() {
        Assertions.assertTrue(BoardIdleExits.tooLateInTheDay(false, 1));
    }

    @Test
    void foundWork_orNothingRejectedForTime_doesNot() {
        Assertions.assertFalse(BoardIdleExits.tooLateInTheDay(true, 1));
        Assertions.assertFalse(BoardIdleExits.tooLateInTheDay(false, 0));
    }
}
