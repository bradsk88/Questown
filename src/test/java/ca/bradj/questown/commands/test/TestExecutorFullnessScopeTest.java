package ca.bradj.questown.commands.test;

import ca.bradj.questown.core.VillagerUUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

/**
 * Pins the fullness-check scoping fix (ticket
 * {@code .scratch/autotest-suite/eating-pair-deterministic-fullness-fail.md}).
 *
 * <p>The check asserts on exactly the townies the scenario assigned its job
 * to — the flag's handle contents at the job-assignment phase, intersected
 * with the check-time contents. A stray townie from an earlier scenario
 * registers into the handle *after* that phase (the spawn wait already
 * passed, so it is not in the snapshot; it keeps its old job, whose
 * post-extract rules carry no HUNGER_FILL) and decays deterministically
 * below the assertion - the 2026-10-15 eat_direct -12 % failure.
 */
class TestExecutorFullnessScopeTest {

    @Test
    void strayArrivingAfterAssignment_isExcludedFromTargets() {
        VillagerUUID scenarioTownie = VillagerUUID.random();
        VillagerUUID stray = VillagerUUID.random();
        Set<VillagerUUID> targets = TestExecutor.scenarioTownieTargets(
                Set.of(scenarioTownie, stray),
                Set.of(scenarioTownie));
        Assertions.assertEquals(Set.of(scenarioTownie), targets);
    }

    @Test
    void towniePresentAtAssignment_isIncludedInTargets() {
        // A townie in the handle at assignment got the scenario's job and was
        // drained by it: it is part of the scenario's world and in scope.
        VillagerUUID scenarioTownie = VillagerUUID.random();
        VillagerUUID earlyStray = VillagerUUID.random();
        Set<VillagerUUID> targets = TestExecutor.scenarioTownieTargets(
                Set.of(scenarioTownie, earlyStray),
                Set.of(scenarioTownie, earlyStray));
        Assertions.assertEquals(Set.of(scenarioTownie, earlyStray), targets);
    }

    @Test
    void deadScenarioTownie_isAbsentFromTargets() {
        // The check intersects with current contents: a scenario townie that
        // died before the check simply is not a target (no stats to read).
        VillagerUUID scenarioTownie = VillagerUUID.random();
        VillagerUUID earlyStray = VillagerUUID.random();
        Set<VillagerUUID> targets = TestExecutor.scenarioTownieTargets(
                Set.of(earlyStray),
                Set.of(scenarioTownie, earlyStray));
        Assertions.assertEquals(Set.of(earlyStray), targets);
    }

    @Test
    void nullAssignmentSnapshot_fallsBackToAllTownies() {
        VillagerUUID a = VillagerUUID.random();
        VillagerUUID b = VillagerUUID.random();
        Set<VillagerUUID> targets = TestExecutor.scenarioTownieTargets(
                Set.of(a, b),
                null);
        Assertions.assertEquals(Set.of(a, b), targets);
    }

    @Test
    void emptyCurrent_returnsEmpty() {
        VillagerUUID stray = VillagerUUID.random();
        Set<VillagerUUID> targets = TestExecutor.scenarioTownieTargets(
                Set.of(),
                Set.of(stray));
        Assertions.assertTrue(targets.isEmpty());
    }
}
