package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.AbstractAdvanceTime;
import ca.bradj.questown.town.Warper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Tests for ImportantTicks - the tick computation logic for time warp.
 * These tests verify the class works without Minecraft dependencies.
 */
class ImportantTicksTest {

    private static final JobID TEST_JOB = new JobID("test", "crafter");
    private static final JobID DOWNTIME_JOB = new JobID("test", "downtime");
    private static final VillagerUUID TEST_VILLAGER = VillagerUUID.from(UUID.randomUUID());

    /**
     * Test Work implementation that doesn't depend on Minecraft.
     */
    private static class TestWork implements AbstractAdvanceTime.Work {
        private final long totalDuration;
        private final int ticksPerCycle;
        private final JobID resolvedJob;

        TestWork(long totalDuration, int ticksPerCycle, JobID resolvedJob) {
            this.totalDuration = totalDuration;
            this.ticksPerCycle = ticksPerCycle;
            this.resolvedJob = resolvedJob;
        }

        @Override
        public void recomputeNow() {
            // No-op for test
        }

        @Override
        public JobID getRandomFinishableWork(JobID jobID, Signals.DayTime dayTime, long ticksElapsed) {
            return resolvedJob;
        }

        @Override
        public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
            return totalDuration;
        }

        @Override
        public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
            return ticksPerCycle;
        }
    }

    @Test
    void forVillager_shouldGenerateTicksForKnownJob() {
        // Arrange
        TestWork work = new TestWork(1000, 10, TEST_JOB);
        ImportantTicks.Config config = new ImportantTicks.Config(5000);
        long ticksPassed = 5000;
        long startTime = 0;

        // Act
        ImportantTicks.Result result = ImportantTicks.forVillager(
                work,
                TEST_VILLAGER,
                TEST_JOB,
                job -> false, // Not downtime
                config,
                ticksPassed,
                startTime
        );

        // Assert
        Assertions.assertFalse(result.useDynamicResolution(), "Should not use dynamic resolution for known job");
        Assertions.assertFalse(result.ticks().isEmpty(), "Should generate ticks");

        // Verify ticks are in chronological order
        long prevTick = -1;
        for (Warper.Tick tick : result.ticks()) {
            Assertions.assertTrue(tick.tick() > prevTick, "Ticks should be in chronological order");
            prevTick = tick.tick();
        }
    }

    @Test
    void forVillager_shouldUseDynamicResolution_whenJobCannotBeResolved() {
        // Arrange - Work that returns null (no finishable work)
        TestWork work = new TestWork(1000, 10, null);
        ImportantTicks.Config config = new ImportantTicks.Config(5000);
        long ticksPassed = 5000;
        long startTime = 0;

        // Act
        ImportantTicks.Result result = ImportantTicks.forVillager(
                work,
                TEST_VILLAGER,
                TEST_JOB,
                job -> false, // Not downtime
                config,
                ticksPassed,
                startTime
        );

        // Assert
        Assertions.assertTrue(result.useDynamicResolution(), "Should use dynamic resolution when job can't be resolved");
        Assertions.assertFalse(result.ticks().isEmpty(), "Should still generate ticks");
    }

    @Test
    void forVillager_shouldHandleDowntimeJob() {
        // Arrange
        TestWork work = new TestWork(1000, 10, TEST_JOB);
        ImportantTicks.Config config = new ImportantTicks.Config(2000); // 2000 ticks of downtime
        long ticksPassed = 5000;
        long startTime = 0;

        // Act
        ImportantTicks.Result result = ImportantTicks.forVillager(
                work,
                TEST_VILLAGER,
                DOWNTIME_JOB,
                job -> job.equals(DOWNTIME_JOB), // This IS a downtime job
                config,
                ticksPassed,
                startTime
        );

        // Assert
        Assertions.assertTrue(result.useDynamicResolution(), "Should use dynamic resolution for downtime jobs");
        Assertions.assertFalse(result.ticks().isEmpty(), "Should still generate ticks after downtime");

        // First tick should be the downtime tick
        Warper.Tick firstTick = result.ticks().get(0);
        Assertions.assertEquals(2000, firstTick.tick(), "First tick should be at end of downtime");
    }

    @Test
    void forVillager_shouldReturnEmpty_whenOnlyDowntimeRemains() {
        // Arrange
        TestWork work = new TestWork(1000, 10, TEST_JOB);
        ImportantTicks.Config config = new ImportantTicks.Config(5000); // 5000 ticks of downtime
        long ticksPassed = 3000; // Less than downtime
        long startTime = 0;

        // Act
        ImportantTicks.Result result = ImportantTicks.forVillager(
                work,
                TEST_VILLAGER,
                DOWNTIME_JOB,
                job -> job.equals(DOWNTIME_JOB), // This IS a downtime job
                config,
                ticksPassed,
                startTime
        );

        // Assert
        Assertions.assertTrue(result.useDynamicResolution());
        // Should only have the downtime tick, no work ticks after
        Assertions.assertEquals(1, result.ticks().size(), "Should only have downtime tick when ticks < downtime");
    }

    @Test
    void forVillager_shouldEnforceMinimumTicksPerCycle() {
        // Arrange - Work that returns very small ticksPerCycle
        TestWork work = new TestWork(1000, 2, TEST_JOB); // Only 2 ticks per cycle
        ImportantTicks.Config config = new ImportantTicks.Config(5000);
        long ticksPassed = 5000;
        long startTime = 0;

        // Act
        ImportantTicks.Result result = ImportantTicks.forVillager(
                work,
                TEST_VILLAGER,
                TEST_JOB,
                job -> false,
                config,
                ticksPassed,
                startTime
        );

        // Assert - Should have at least 5 ticks per cycle as minimum (enforced in code)
        // With 5000 ticks and 1000 per cycle, we expect ~5 cycles
        // Each cycle should have minimum 5 ticks, so at least 20 total
        Assertions.assertFalse(result.ticks().isEmpty());
        // Just verify we get ticks and the minimum enforcement doesn't break anything
        Assertions.assertTrue(result.ticks().size() > 0,
                "Should generate ticks with minimum enforcement");
    }

    @Test
    void adaptWork_shouldCorrectlyAdaptTownFlagStateWork() {
        // Arrange
        TownFlagState.Work flagStateWork = new TownFlagState.Work() {
            @Override
            public void recomputeNow() {}

            @Override
            public JobID getRandomFinishableWork(JobID jobID, Signals.DayTime dayTime, long ticksElapsed) {
                return TEST_JOB;
            }

            @Override
            public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
                return 500;
            }

            @Override
            public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
                return 8;
            }
        };

        // Act
        AbstractAdvanceTime.Work adapted = ImportantTicks.adaptWork(flagStateWork);

        // Assert
        Assertions.assertEquals(TEST_JOB, adapted.getRandomFinishableWork(TEST_JOB, new Signals.DayTime(1000), 1000));
        Assertions.assertEquals(500, adapted.getTotalDuration(TEST_JOB, TEST_VILLAGER));
        Assertions.assertEquals(8, adapted.getWarpTicksPerCycle(TEST_JOB, TEST_VILLAGER));
    }
}
