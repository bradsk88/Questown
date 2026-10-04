package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the "unlocked with the root job at staff" decision — a job is in its
 * root's initial set if it is a progression root (no parent) OR its job JSON
 * opted in via the `unlocked_with_root_job` flag (see
 * `.scratch/cook-skill-unlock/cook-skill-unlock.md`).
 *
 * <p>The decision is pure (parent + flag), so it is testable without the Forge
 * registry; the JSON parse (`ResourceJobLoader`) and the `getDefaultWork`
 * consumers are the registry-bound half, covered by compile + the held
 * self_sufficiency run.
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
        // cook/fish / the recipe jobs: parent set, no tag — the level tree
        // must stay progression-gated (user ruling: never bulk-grant recipes).
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
