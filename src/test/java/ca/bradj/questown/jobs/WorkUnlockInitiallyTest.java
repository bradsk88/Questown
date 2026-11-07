package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A job joins its root's starting set if it has no parent or says {@code unlocked_with_root_job}. */
class WorkUnlockInitiallyTest {

    private static final JobID COOK_ROOT = new JobID("cook", "extract");
    private static final JobID CHILD = new JobID("cook", "fuel");

    @Test
    void progressionRootIsUnlockedInitially() {
        assertTrue(Work.isUnlockedInitially(null, false));
    }

    @Test
    void childJobStaysGatedWithoutFlag() {
        assertFalse(Work.isUnlockedInitially(COOK_ROOT, false));
    }

    @Test
    void childJobUnlocksWhenFlagged() {
        assertTrue(Work.isUnlockedInitially(COOK_ROOT, true));
    }

    @Test
    void rootWithTagIsStillUnlockedInitially() {
        assertTrue(Work.isUnlockedInitially(null, true));
    }
}
