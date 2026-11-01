package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A job is in its root's initial set if it is a progression root (no parent) or its job JSON opts
 * in via `unlocked_with_root_job`. The decision is pure (parent + flag); the JSON parse and the
 * `getDefaultWork` consumers are registry-bound.
 */
class WorkUnlockInitiallyTest {

    private static final JobID COOK_ROOT = new JobID("cook", "extract");
    private static final JobID CHILD = new JobID("cook", "fuel");

    @Test
    void progressionRootIsUnlockedInitially() {
        // cook/extract: parent null, no tag — today's behavior, must survive.
        assertTrue(Work.isUnlockedInitially(null, false));
    }

    @Test
    void childJobStaysGatedWithoutFlag() {
        // cook/fish / the recipe jobs: parent set, no tag — they stay progression-gated.
        assertFalse(Work.isUnlockedInitially(COOK_ROOT, false));
    }

    @Test
    void childJobUnlocksWhenFlagged() {
        // cook/fuel: parent set, tagged — the new guaranteed second job.
        assertTrue(Work.isUnlockedInitially(COOK_ROOT, true));
    }

    @Test
    void rootWithTagIsStillUnlockedInitially() {
        assertTrue(Work.isUnlockedInitially(null, true));
    }
}
