package ca.bradj.questown.jobs.scenarios;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.AbstractAdvanceTime;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.Warper;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Executes scenarios using AbstractAdvanceTime (warp-based simulation).
 * This simulates what happens when a player leaves and returns to a town.
 */
public class WarpScenarioExecutor implements ScenarioExecutor {

    @Override
    public String getExecutorType() {
        return "warp";
    }

    @Override
    public ExecutionResult execute(VillagerWorkScenario scenario, long ticks) {
        // Build initial state from scenario
        TestTownState initialState = buildInitialState(scenario);

        // Create test implementations
        TestWork work = new TestWork(scenario);
        TestWarperFactory warperFactory = new TestWarperFactory(scenario);
        TestWarpLogger logger = new TestWarpLogger();

        // Create and run the advancer
        TestAdvanceTime advancer = new TestAdvanceTime();
        AbstractAdvanceTime.Result<TestTownState> result = advancer.advanceTime(
                initialState,
                ticks,
                0L, // currentTick
                work,
                warperFactory,
                null, // No cook resolver for now
                null, // No level needed for test
                jobId -> false, // No downtime jobs in tests
                1000L, // downtime ticks
                logger
        );

        return new ExecutionResult(
                result.state(),
                warperFactory.cyclesCompleted,
                warperFactory.foodConsumed,
                ticks,
                getExecutorType()
        );
    }

    private TestTownState buildInitialState(VillagerWorkScenario scenario) {
        // Create container with initial items
        TestTownState.TestContainer container = new TestTownState.TestContainer(27, TestTownState::emptyItem);
        int slot = 0;
        for (Map.Entry<String, Integer> entry : scenario.containerItems().entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                container.setItemAt(slot++, new TestItem(entry.getKey()));
            }
        }

        // Create villager with initial items
        List<TestItem> villagerItemsList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            villagerItemsList.add(new TestItem("")); // Empty slots
        }
        int vSlot = 0;
        for (Map.Entry<String, Integer> entry : scenario.villagerItems().entrySet()) {
            for (int i = 0; i < entry.getValue() && vSlot < 6; i++) {
                villagerItemsList.set(vSlot++, new TestItem(entry.getKey()));
            }
        }

        // Create villager data
        UUID uuid = UUID.randomUUID();
        ImmutableSnapshot<TestItem, ?> snapshot = new TestSnapshot(
                scenario.initialJob(),
                ImmutableList.copyOf(villagerItemsList),
                scenario.initialStatus()
        );
        TownState.VillagerData<TestItem> villagerData = new TownState.VillagerData<>(
                0, 0, 0, snapshot, uuid
        );

        // Build the town state
        ContainerTarget<TestTownState.TestContainer, TestItem> containerTarget =
                TestTownState.containerAt(new Position(0, 0), container);

        return new TestTownState(
                ImmutableList.of(villagerData),
                ImmutableList.of(containerTarget),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableList.of(),
                ImmutableMap.of(),
                0L
        );
    }

    // --- Test Implementations ---

    private static class TestAdvanceTime extends AbstractAdvanceTime<
            TestTownState.TestContainer,
            TestItem,
            TestItem,
            Position,
            TestTownState,
            Void
            > {
        @Override
        protected TestTownState finalizeState(TestTownState state, long currentTick) {
            return state;
        }
    }

    private static class TestWork implements AbstractAdvanceTime.Work {
        private final VillagerWorkScenario scenario;

        TestWork(VillagerWorkScenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public void recomputeNow() {
            // No-op for tests
        }

        @Override
        public @Nullable JobID getRandomFinishableWork(JobID jobID, Signals.DayTime dayTime, long ticksElapsed) {
            // Return the scenario's job
            return scenario.initialJob();
        }

        @Override
        public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
            // Default duration for test jobs
            return 1000L;
        }

        @Override
        public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
            return 1000; // Match realtime TICKS_PER_CYCLE
        }
    }

    private static class TestWarperFactory implements AbstractAdvanceTime.WarperFactory<Void, TestTownState> {
        final VillagerWorkScenario scenario;
        int cyclesCompleted = 0;
        int foodConsumed = 0;

        TestWarperFactory(VillagerWorkScenario scenario) {
            this.scenario = scenario;
        }

        @Override
        public Warper<Void, TestTownState> createWarper(
                AbstractAdvanceTime.Work work,
                JobID fallbackJobID,
                int villagerIndex
        ) {
            return new TestWarper(this, scenario);
        }
    }

    private static class TestWarper implements Warper<Void, TestTownState> {
        private static final int TICKS_PER_CYCLE = 1000;
        private final TestWarperFactory factory;
        private final VillagerWorkScenario scenario;
        private long accumulatedTicks = 0;

        TestWarper(TestWarperFactory factory, VillagerWorkScenario scenario) {
            this.factory = factory;
            this.scenario = scenario;
        }

        @Override
        public TestTownState warp(Void lootSource, TestTownState liveState, long currentTick, long ticksPassed, int villagerNum) {
            // Check if work is possible based on scenario status
            if (scenario.initialStatus() == ProductionStatus.NO_SUPPLIES ||
                scenario.initialStatus() == ProductionStatus.NO_JOBSITE) {
                // Can't do work
                return liveState;
            }

            // Can't work if containers are empty (no supplies to collect)
            if (scenario.containerItems().isEmpty()) {
                return liveState;
            }

            // Accumulate ticks and count complete cycles
            accumulatedTicks += ticksPassed;
            int cyclesThisWarp = (int) (accumulatedTicks / TICKS_PER_CYCLE);
            accumulatedTicks = accumulatedTicks % TICKS_PER_CYCLE;

            factory.cyclesCompleted += cyclesThisWarp;

            // Track food consumption for gatherer jobs
            if (isGathererJob(scenario.initialJob())) {
                factory.foodConsumed += cyclesThisWarp;
            }

            TestTownState result = liveState;
            for (int i = 0; i < cyclesThisWarp; i++) {
                result = result.withCycleCompleted();
            }
            return result;
        }

        private boolean isGathererJob(JobID jobId) {
            return jobId.rootId().contains("gatherer") ||
                   jobId.jobId().contains("gatherer");
        }

        @Override
        public Collection<Tick> getTicks(long referenceTick, long ticksPassed) {
            // Generate ticks at 1000-tick intervals
            List<Tick> ticks = new ArrayList<>();
            long prev = 0;
            for (long t = TICKS_PER_CYCLE; t <= ticksPassed; t += TICKS_PER_CYCLE) {
                ticks.add(new Tick(referenceTick + t, t - prev));
                prev = t;
            }
            return ticks;
        }
    }

    private static class TestWarpLogger implements AbstractAdvanceTime.WarpLogger {
        @Override
        public void log(String message, Object... args) {
            // Silent for tests
        }

        @Override
        public void logDetail(String message, Object... args) {
            // Silent for tests
        }
    }

    /**
     * Test implementation of ImmutableSnapshot.
     */
    private static class TestSnapshot implements ImmutableSnapshot<TestItem, TestSnapshot> {
        private final JobID jobId;
        private final ImmutableList<TestItem> items;
        private final ca.bradj.questown.jobs.production.ProductionStatus status;

        TestSnapshot(JobID jobId, ImmutableList<TestItem> items, ca.bradj.questown.jobs.production.ProductionStatus status) {
            this.jobId = jobId;
            this.items = items;
            this.status = status;
        }

        @Override
        public JobID jobId() {
            return jobId;
        }

        @Override
        public ImmutableList<TestItem> items() {
            return items;
        }

        @Override
        public TestSnapshot withSetItem(int idx, TestItem item) {
            List<TestItem> newItems = new ArrayList<>(items);
            newItems.set(idx, item);
            return new TestSnapshot(jobId, ImmutableList.copyOf(newItems), status);
        }

        @Override
        public TestSnapshot withItems(ImmutableList<TestItem> items) {
            return new TestSnapshot(jobId, items, status);
        }

        @Override
        public String statusStringValue() {
            return status != null ? status.name() : "UNKNOWN";
        }

        @Override
        public String jobStringValue() {
            return jobId != null ? jobId.jobId() : "UNKNOWN";
        }

        public @Nullable Object getStatusForWarp() {
            return status;
        }
    }
}
