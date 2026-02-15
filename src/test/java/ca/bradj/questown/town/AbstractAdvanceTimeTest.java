package ca.bradj.questown.town;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.entity.ImportantTicks;
import ca.bradj.questown.town.entity.TownFlagState;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tests for AbstractAdvanceTime interfaces and ImportantTicks integration.
 * These tests verify the core time warp abstractions work without Minecraft dependencies.
 */
class AbstractAdvanceTimeTest {

    private static final JobID TEST_JOB = new JobID("test", "crafter");
    private static final VillagerUUID TEST_VILLAGER = VillagerUUID.from(UUID.randomUUID());

    /**
     * Test Work implementation for AbstractAdvanceTime.Work - core interface for job resolution
     */
    static class TestWork implements AbstractAdvanceTime.Work {
        boolean recomputeCalled = false;
        int getRandomFinishableWorkCalls = 0;
        int getTotalDurationCalls = 0;
        int getWarpTicksPerCycleCalls = 0;

        @Override
        public void recomputeNow() {
            recomputeCalled = true;
        }

        @Override
        public @Nullable JobID getRandomFinishableWork(JobID jobID, Signals.DayTime dayTime, long ticksElapsed) {
            getRandomFinishableWorkCalls++;
            return TEST_JOB;
        }

        @Override
        public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
            getTotalDurationCalls++;
            return 1000;
        }

        @Override
        public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
            getWarpTicksPerCycleCalls++;
            return 10;
        }
    }

    /**
     * Test Work implementation for TownFlagState.Work - for use with ImportantTicks.adaptWork()
     */
    static class TestFlagStateWork implements TownFlagState.Work {
        boolean recomputeCalled = false;
        int getRandomFinishableWorkCalls = 0;
        int getTotalDurationCalls = 0;
        int getWarpTicksPerCycleCalls = 0;

        @Override
        public void recomputeNow() {
            recomputeCalled = true;
        }

        @Override
        public @Nullable JobID getRandomFinishableWork(JobID jobID, Signals.DayTime dayTime, long ticksElapsed) {
            getRandomFinishableWorkCalls++;
            return TEST_JOB;
        }

        @Override
        public java.util.List<JobID> getPreselectedJobs(JobID currentJob) {
            return java.util.List.of(TEST_JOB);
        }

        @Override
        public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
            getTotalDurationCalls++;
            return 1000;
        }

        @Override
        public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
            getWarpTicksPerCycleCalls++;
            return 10;
        }
    }

    /**
     * Test logger - verifies logging interface works
     */
    static class TestLogger implements AbstractAdvanceTime.WarpLogger {
        List<String> logs = new ArrayList<>();
        List<String> detailLogs = new ArrayList<>();

        @Override
        public void log(String message, Object... args) {
            logs.add(message);
        }

        @Override
        public void logDetail(String message, Object... args) {
            detailLogs.add(message);
        }
    }

    @Test
    void workInterface_shouldBeCallable_withoutMinecraft() {
        // Arrange
        TestWork work = new TestWork();

        // Act - Call all methods that would normally call Minecraft registries
        work.recomputeNow();
        JobID resolvedJob = work.getRandomFinishableWork(TEST_JOB, new Signals.DayTime(1000), 5000);
        long duration = work.getTotalDuration(TEST_JOB, TEST_VILLAGER);
        int ticksPerCycle = work.getWarpTicksPerCycle(TEST_JOB, TEST_VILLAGER);

        // Assert - All methods callable and return expected values
        Assertions.assertTrue(work.recomputeCalled);
        Assertions.assertEquals(TEST_JOB, resolvedJob);
        Assertions.assertEquals(1000, duration);
        Assertions.assertEquals(10, ticksPerCycle);
    }

    @Test
    void warpLogger_shouldLogMessages_withoutMinecraft() {
        // Arrange
        TestLogger logger = new TestLogger();

        // Act
        logger.log("Processing {} villagers", 5);
        logger.logDetail("Villager {} at tick {}", "v1", 1000);

        // Assert
        Assertions.assertEquals(1, logger.logs.size());
        Assertions.assertEquals(1, logger.detailLogs.size());
    }

    @Test
    void importantTicks_shouldComputeTicks_usingWorkInterface() {
        // Arrange - Use TownFlagState.Work which can be adapted
        TestFlagStateWork work = new TestFlagStateWork();
        ImportantTicks.Config config = new ImportantTicks.Config(5000);

        // Act - Use ImportantTicks with our test Work implementation adapted to AbstractAdvanceTime.Work
        ImportantTicks.Result result = ImportantTicks.forVillager(
                ImportantTicks.adaptWork(work), // Adapt TownFlagState.Work to AbstractAdvanceTime.Work
                TEST_VILLAGER,
                TEST_JOB,
                job -> false, // Not downtime
                config,
                10000, // ticksPassed
                0 // startingAtGameTime
        );

        // Assert
        Assertions.assertFalse(result.ticks().isEmpty(), "Should generate ticks");
        Assertions.assertTrue(work.recomputeCalled, "Should call recomputeNow");
        Assertions.assertTrue(work.getRandomFinishableWorkCalls > 0, "Should call getRandomFinishableWork");
        Assertions.assertTrue(work.getTotalDurationCalls > 0, "Should call getTotalDuration");
        Assertions.assertTrue(work.getWarpTicksPerCycleCalls > 0, "Should call getWarpTicksPerCycle");
    }

    @Test
    void warperTickRecord_shouldBeCreatable_withoutMinecraft() {
        // Arrange & Act
        Warper.Tick tick = new Warper.Tick(1000, 100);

        // Assert
        Assertions.assertEquals(1000, tick.tick());
        Assertions.assertEquals(100, tick.ticksSincePrevious());
    }

    @Test
    void resultRecord_shouldBeCreatable_withoutMinecraft() {
        // Arrange & Act
        AbstractAdvanceTime.Result<String> result = new AbstractAdvanceTime.Result<>(
                "test-state",
                500,
                25
        );

        // Assert
        Assertions.assertEquals("test-state", result.state());
        Assertions.assertEquals(500, result.processingTimeMs());
        Assertions.assertEquals(25, result.totalWarpSteps());
    }

    @Test
    void warperFactoryInterface_shouldBeImplementable_withoutMinecraft() {
        // Arrange
        AbstractAdvanceTime.WarperFactory<Object, TownState<?, ?, ?, ?, ?>> factory =
                (work, fallbackJobID, villagerIndex) -> {
                    // Return a simple warper that tracks calls
                    return new Warper<>() {
                        @Override
                        public TownState<?, ?, ?, ?, ?> warp(Object level, TownState<?, ?, ?, ?, ?> liveState, long currentTick, long ticksPassed, int villagerNum) {
                            return liveState;
                        }

                        @Override
                        public java.util.Collection<Tick> getTicks(long referenceTick, long ticksPassed) {
                            return ImmutableList.of(new Tick(referenceTick + 100, 100));
                        }
                    };
                };

        // Act
        Warper<Object, TownState<?, ?, ?, ?, ?>> warper = factory.createWarper(
                new TestWork(),
                TEST_JOB,
                0
        );

        // Assert
        Assertions.assertNotNull(warper);
        Assertions.assertEquals(1, warper.getTicks(0, 1000).size());
    }

    @Test
    void cookResolverInterface_shouldBeImplementable_withoutMinecraft() {
        // Arrange
        boolean[] called = {false};
        AbstractAdvanceTime.CookResolver<String, Object> resolver = (state, level, ticksPerCycle, availableTicks) -> {
            called[0] = true;
            return state + "_cooked";
        };

        // Act
        String result = resolver.resolveCooking("initial", new Object(), 100, 5000);

        // Assert
        Assertions.assertTrue(called[0]);
        Assertions.assertEquals("initial_cooked", result);
    }
}
