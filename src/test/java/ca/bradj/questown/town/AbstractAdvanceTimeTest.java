package ca.bradj.questown.town;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.entity.ImportantTicks;
import ca.bradj.questown.town.entity.TownFlagState;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

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

    // ---- Seam tests: actual advanceTime, no simulation of warp logic (ADR-0006) ----
    //
    // These exercise the actual AbstractAdvanceTime.advanceTime warp loop with stub town/villager/
    // warper types, asserting the dual-timeline behaviour: villager labour rides the productive
    // budget while passive effects ride the wall-clock budget (incl. night).

    private static final long DAY = Signals.FULL_DAY_TICKS;            // 24000
    private static final long PRODUCTIVE_DAY = Signals.PRODUCTIVE_DAY_END_TICK; // 11500
    private static final long NIGHT = DAY - PRODUCTIVE_DAY;            // 12500

    /** Minimal concrete TownState so the actual advanceTime loop has something to iterate. */
    static class SeamTown extends TownState<ContainerTarget.Container<TestItem>, TestItem, TestItem, Position, SeamTown> {
        SeamTown(List<VillagerData<TestItem>> villagers, long worldTimeAtSleep) {
            super(villagers, List.of(), ImmutableMap.of(), ImmutableMap.of(), List.of(), ImmutableMap.of(), worldTimeAtSleep);
        }

        @Override
        protected SeamTown newTownState(
                ImmutableList<VillagerData<TestItem>> villagers,
                ImmutableList<ContainerTarget<ContainerTarget.Container<TestItem>, TestItem>> containers,
                ImmutableMap<Position, State> workStates,
                ImmutableMap<Position, Integer> workTimers,
                ImmutableList<Position> gates,
                ImmutableMap<UUID, Boolean> blocksOfProgress,
                long worldTimeAtSleep
        ) {
            return new SeamTown(villagers, worldTimeAtSleep);
        }
    }

    /** Concrete AbstractAdvanceTime that emits a preset list of labour ticks (no MC, no registries). */
    static class SeamAdvanceTime extends AbstractAdvanceTime<
            ContainerTarget.Container<TestItem>, TestItem, TestItem, Position, SeamTown, Object> {
        private final ImmutableList<Warper.Tick> presetTicks;

        SeamAdvanceTime(ImmutableList<Warper.Tick> presetTicks) {
            this.presetTicks = presetTicks;
        }

        @Override
        protected ImmutableList<Warper.Tick> computeImportantTicks(
                Work work, VillagerUUID uuid, JobID jobID, Predicate<JobID> downtimeCheck,
                long downtimeTicks, long ticksPassed, long currentTick
        ) {
            return presetTicks;
        }

        @Override
        protected SeamTown finalizeState(SeamTown state, long currentTick) {
            return state;
        }
    }

    private static ImmutableSnapshot<TestItem, ?> stubJournal() {
        return new ImmutableSnapshot<TestItem, Object>() {
            @Override
            public Object withSetItem(int itemIndex, TestItem item) {
                return this;
            }

            @Override
            public Object withItems(ImmutableList<TestItem> items) {
                return this;
            }

            @Override
            public String statusStringValue() {
                return "";
            }

            @Override
            public String jobStringValue() {
                return "";
            }

            @Override
            public ImmutableList<TestItem> items() {
                return ImmutableList.of();
            }

            @Override
            public JobID jobId() {
                return TEST_JOB;
            }
        };
    }

    private static SeamTown singleVillagerTown() {
        TownState.VillagerData<TestItem> v = new TownState.VillagerData<>(
                0, 0, 0, stubJournal(), UUID.randomUUID()
        );
        return new SeamTown(List.of(v), 0);
    }

    private static ImmutableList<Warper.Tick> laborTicksAt(long windowEnd, long... productiveOffsets) {
        ImmutableList.Builder<Warper.Tick> b = ImmutableList.builder();
        long prev = 0;
        for (long off : productiveOffsets) {
            b.add(new Warper.Tick(windowEnd + off, off - prev));
            prev = off;
        }
        return b.build();
    }

    @Test
    void advanceTime_passiveDeltaTotalsWallClockTicks_whileLaborStaysOnProductiveBudget() {
        // Two-day window [0, 35500]: day1 productive + night + day2 productive.
        long windowEnd = 35500;
        long wallClockTicks = 35500;
        long productiveTicks = Signals.calculateProductiveTicks(windowEnd, wallClockTicks); // 23000
        Signals.ProductiveWallClockTimeline timeline =
                Signals.buildProductiveWallClockTimeline(windowEnd, wallClockTicks);
        LongUnaryOperator productiveToWallClock = timeline::wallClockOffsetAt;

        // Labour ticks span both productive days (offsets are productive-space).
        ImmutableList<Warper.Tick> labor = laborTicksAt(windowEnd, 5000, 11000, 12000, 20000, 23000);

        List<Long> passiveDeltas = new ArrayList<>();
        List<Long> passiveCurrentTicks = new ArrayList<>();
        List<Long> laborCurrentTicks = new ArrayList<>();

        AbstractAdvanceTime.WarpTickCallback<SeamTown> passive = (town, currentTick, tickDelta) -> {
            passiveCurrentTicks.add(currentTick);
            passiveDeltas.add(tickDelta);
            return town;
        };

        AbstractAdvanceTime.WarperFactory<Object, SeamTown> factory =
                (work, fallbackJobID, villagerIndex) -> new Warper<>() {
                    @Override
                    public SeamTown warp(Object level, SeamTown liveState, long currentTick, long ticksPassed, int villagerNum) {
                        laborCurrentTicks.add(currentTick);
                        return liveState;
                    }

                    @Override
                    public java.util.Collection<Tick> getTicks(long referenceTick, long ticksPassed) {
                        return List.of();
                    }
                };

        new SeamAdvanceTime(labor).advanceTime(
                singleVillagerTown(),
                productiveTicks,
                wallClockTicks,
                windowEnd,
                productiveToWallClock,
                new TestWork(),
                factory,
                null,
                passive,
                new Object(),
                job -> false,
                0,
                new TestLogger()
        );

        // Passive effects are credited the FULL wall-clock window (incl. the night), telescoping to wallClockTicks.
        long totalPassive = passiveDeltas.stream().mapToLong(Long::longValue).sum();
        Assertions.assertEquals(wallClockTicks, totalPassive,
                "passive tickDelta must total wallClockTicks (night included)");

        // Labour never rides past the productive budget: max labour offset == productiveTicks.
        long maxLaborOffset = laborCurrentTicks.stream().mapToLong(t -> t - windowEnd).max().orElse(-1);
        Assertions.assertEquals(productiveTicks, maxLaborOffset,
                "labour stays on the productive budget");
        Assertions.assertTrue(totalPassive > productiveTicks,
                "wall-clock budget must exceed productive budget for a night-spanning window");
    }

    @Test
    void advanceTime_creditsNightBetweenLaborSteps_soDependencyChainsMature() {
        // A sapling "planted" at a day-1 labour step must be mature (>= a night) by the day-2 step,
        // because advanceTime credits the night between them to the passive currentTick.
        long windowEnd = 35500;
        long wallClockTicks = 35500;
        long productiveTicks = Signals.calculateProductiveTicks(windowEnd, wallClockTicks);
        Signals.ProductiveWallClockTimeline timeline =
                Signals.buildProductiveWallClockTimeline(windowEnd, wallClockTicks);

        // One labour step just before dusk (offset 11000, day1) and one just after dawn (offset 12000, day2).
        ImmutableList<Warper.Tick> labor = laborTicksAt(windowEnd, 11000, 12000);

        List<Long> passiveCurrentTicks = new ArrayList<>();
        AbstractAdvanceTime.WarpTickCallback<SeamTown> passive = (town, currentTick, tickDelta) -> {
            passiveCurrentTicks.add(currentTick);
            return town;
        };

        AbstractAdvanceTime.WarperFactory<Object, SeamTown> factory =
                (work, fallbackJobID, villagerIndex) -> new Warper<>() {
                    @Override
                    public SeamTown warp(Object level, SeamTown liveState, long currentTick, long ticksPassed, int villagerNum) {
                        return liveState;
                    }

                    @Override
                    public java.util.Collection<Tick> getTicks(long referenceTick, long ticksPassed) {
                        return List.of();
                    }
                };

        new SeamAdvanceTime(labor).advanceTime(
                singleVillagerTown(),
                productiveTicks,
                wallClockTicks,
                windowEnd,
                timeline::wallClockOffsetAt,
                new TestWork(),
                factory,
                null,
                passive,
                new Object(),
                job -> false,
                0,
                new TestLogger()
        );

        // The first two fires are the two labour steps (a trailing fire may follow, covering
        // day-2's remaining productive tail — that's expected and irrelevant here).
        Assertions.assertTrue(passiveCurrentTicks.size() >= 2, "both labour steps fired the passive hook");
        long dawnToDuskGap = passiveCurrentTicks.get(1) - passiveCurrentTicks.get(0);
        // The night (12500) is credited, so the gap far exceeds the 1000-tick productive gap between
        // the two labour offsets (11000 -> 12000). Under the old productive-only code it was 1000.
        Assertions.assertTrue(dawnToDuskGap >= NIGHT,
                "night must be credited between day-1 and day-2 labour steps (gap=" + dawnToDuskGap + ")");
    }

    @Test
    void advanceTime_allNightWindow_runsPassiveEffectsWithNoLabor() {
        // Pure-night window: zero productive ticks, but passive effects must still run.
        long windowEnd = 17000;     // deep night
        long wallClockTicks = 2000;
        long productiveTicks = Signals.calculateProductiveTicks(windowEnd, wallClockTicks); // 0
        Assertions.assertEquals(0, productiveTicks, "precondition: all-night window");
        Signals.ProductiveWallClockTimeline timeline =
                Signals.buildProductiveWallClockTimeline(windowEnd, wallClockTicks);

        List<Long> passiveDeltas = new ArrayList<>();
        boolean[] laborRan = {false};

        AbstractAdvanceTime.WarpTickCallback<SeamTown> passive = (town, currentTick, tickDelta) -> {
            passiveDeltas.add(tickDelta);
            return town;
        };
        AbstractAdvanceTime.WarperFactory<Object, SeamTown> factory =
                (work, fallbackJobID, villagerIndex) -> new Warper<>() {
                    @Override
                    public SeamTown warp(Object level, SeamTown liveState, long currentTick, long ticksPassed, int villagerNum) {
                        laborRan[0] = true;
                        return liveState;
                    }

                    @Override
                    public java.util.Collection<Tick> getTicks(long referenceTick, long ticksPassed) {
                        return List.of();
                    }
                };

        new SeamAdvanceTime(laborTicksAt(windowEnd, 100)).advanceTime(
                singleVillagerTown(),
                productiveTicks,
                wallClockTicks,
                windowEnd,
                timeline::wallClockOffsetAt,
                new TestWork(),
                factory,
                null,
                passive,
                new Object(),
                job -> false,
                0,
                new TestLogger()
        );

        Assertions.assertFalse(laborRan[0], "no labour runs during an all-night window");
        Assertions.assertEquals(wallClockTicks, passiveDeltas.stream().mapToLong(Long::longValue).sum(),
                "passive effects still run for the whole night");
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
