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
}
