package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkProgressTest {

    /** A work bar on the status icon of any work-based job. */
    @Test
    void fractionIsTheWorkDone() {
        Assertions.assertEquals(0f, WorkProgress.fraction(40, 40));
        Assertions.assertEquals(0.75f, WorkProgress.fraction(10, 40));
    }

    @Test
    void noWork_noBar() {
        Assertions.assertEquals(-1f, WorkProgress.fraction(0, 40));
        Assertions.assertEquals(-1f, WorkProgress.fraction(5, 0));
    }
}
