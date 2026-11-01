package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** A finished BOP deposit relaxes like a fed diner instead of staying in bop_depositing. */
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
