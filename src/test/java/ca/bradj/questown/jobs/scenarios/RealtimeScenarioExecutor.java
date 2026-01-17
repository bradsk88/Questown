package ca.bradj.questown.jobs.scenarios;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Executes scenarios using a tick-by-tick simulation (similar to JobLogic.tick).
 * This simulates what happens when a player is present and the game runs normally.
 * <p>
 * Note: This is a simplified simulation that captures the key behaviors without
 * implementing the full JobLogic complexity. It's designed to produce comparable
 * outcomes to the warp executor for the same scenarios.
 */
public class RealtimeScenarioExecutor implements ScenarioExecutor {

    // Ticks per work cycle (collect -> work -> extract -> drop)
    private static final int TICKS_PER_CYCLE = 1000;

    @Override
    public String getExecutorType() {
        return "realtime";
    }

    @Override
    public ExecutionResult execute(VillagerWorkScenario scenario, long ticks) {
        // Build initial state from scenario
        TestTownState state = buildInitialState(scenario);

        int cyclesCompleted = 0;
        int foodConsumed = 0;

        // Simulate tick-by-tick
        SimulationState sim = new SimulationState(scenario, state);

        for (long tick = 0; tick < ticks; tick++) {
            SimulationResult result = simulateTick(sim, tick);

            if (result.cycleCompleted) {
                cyclesCompleted++;
                state = state.withCycleCompleted();
            }
            if (result.foodConsumed) {
                foodConsumed++;
                state = state.withFoodConsumed(1);
            }

            sim.state = state;
        }

        return new ExecutionResult(
                state,
                cyclesCompleted,
                foodConsumed,
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

    private SimulationResult simulateTick(SimulationState sim, long tick) {
        boolean cycleCompleted = false;
        boolean foodConsumed = false;

        // Check if we can do work based on scenario
        if (sim.scenario.initialStatus() == ProductionStatus.NO_SUPPLIES ||
            sim.scenario.initialStatus() == ProductionStatus.NO_JOBSITE) {
            // Can't do work
            return new SimulationResult(false, false);
        }

        // Simple cycle simulation
        sim.ticksInCurrentCycle++;

        if (sim.ticksInCurrentCycle >= TICKS_PER_CYCLE) {
            // Check if we have supplies
            if (hasSuppliesForJob(sim)) {
                cycleCompleted = true;
                sim.ticksInCurrentCycle = 0;

                // Gatherers consume food
                if (isGathererJob(sim.scenario.initialJob())) {
                    foodConsumed = true;
                }
            }
        }

        return new SimulationResult(cycleCompleted, foodConsumed);
    }

    private boolean hasSuppliesForJob(SimulationState sim) {
        // Check if containers have any of the required items
        Map<String, Integer> containerItems = sim.scenario.containerItems();
        return !containerItems.isEmpty();
    }

    private boolean isGathererJob(JobID jobId) {
        return jobId.rootId().contains("gatherer") ||
               jobId.jobId().contains("gatherer");
    }

    // --- Helper Classes ---

    private static class SimulationState {
        final VillagerWorkScenario scenario;
        TestTownState state;
        int ticksInCurrentCycle = 0;

        SimulationState(VillagerWorkScenario scenario, TestTownState state) {
            this.scenario = scenario;
            this.state = state;
        }
    }

    private record SimulationResult(boolean cycleCompleted, boolean foodConsumed) {}

    /**
     * Test implementation of ImmutableSnapshot.
     */
    private static class TestSnapshot implements ImmutableSnapshot<TestItem, TestSnapshot> {
        private final JobID jobId;
        private final ImmutableList<TestItem> items;
        private final ProductionStatus status;

        TestSnapshot(JobID jobId, ImmutableList<TestItem> items, ProductionStatus status) {
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
