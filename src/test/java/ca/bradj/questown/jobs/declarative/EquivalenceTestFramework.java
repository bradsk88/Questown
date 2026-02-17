package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobDefinition;
import ca.bradj.questown.jobs.TestInventory;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.questown.world.TestWorldAccess;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Framework for testing equivalence between DeclarativeJobTicker and AdvanceTime.
 * This verifies both methods produce the same (or virtually the same) outcomes.
 */
public class EquivalenceTestFramework {

    /**
     * Captures the state after running a job simulation.
     * Used to compare ticker vs advancer results.
     */
    public record SimulationResult(
            List<String> inventoryItems,
            boolean productExtracted,
            int jobBlockState,
            int workRemaining
    ) {
        public static SimulationResult capture(
                ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
                TestWorldInteraction worldInteraction,
                TestWorkStatusHandle workStatusHandle,
                Position workspotPos
        ) {
            List<String> items = inventory.getItems().stream()
                    .map(item -> item.value)
                    .collect(Collectors.toList());

            State state = workStatusHandle.getJobBlockState(workspotPos);
            int jobBlockState = state != null ? state.processingState() : 0;
            int workRemaining = state != null ? state.workLeft() : 0;

            return new SimulationResult(
                    items,
                    worldInteraction.wasExtracted(),
                    jobBlockState,
                    workRemaining
            );
        }
    }

    /**
     * Compares two simulation results for equivalence.
     * Returns a detailed diff if they differ.
     */
    public record EquivalenceComparison(
            boolean equivalent,
            String diffMessage
    ) {
        public static EquivalenceComparison compare(
                SimulationResult tickerResult,
                SimulationResult advancerResult
        ) {
            StringBuilder diff = new StringBuilder();
            boolean equivalent = true;

            // Compare inventory items (order may differ)
            List<String> tickerItems = tickerResult.inventoryItems().stream()
                    .filter(s -> !s.isEmpty())
                    .sorted()
                    .collect(Collectors.toList());
            List<String> advancerItems = advancerResult.inventoryItems().stream()
                    .filter(s -> !s.isEmpty())
                    .sorted()
                    .collect(Collectors.toList());

            if (!tickerItems.equals(advancerItems)) {
                equivalent = false;
                diff.append("Inventory differs:\n");
                diff.append("  Ticker:   ").append(tickerItems).append("\n");
                diff.append("  Advancer: ").append(advancerItems).append("\n");
            }

            // Compare extraction state
            if (tickerResult.productExtracted() != advancerResult.productExtracted()) {
                equivalent = false;
                diff.append("Extraction differs:\n");
                diff.append("  Ticker:   ").append(tickerResult.productExtracted()).append("\n");
                diff.append("  Advancer: ").append(advancerResult.productExtracted()).append("\n");
            }

            // Compare job block state
            if (tickerResult.jobBlockState() != advancerResult.jobBlockState()) {
                equivalent = false;
                diff.append("Job block state differs:\n");
                diff.append("  Ticker:   ").append(tickerResult.jobBlockState()).append("\n");
                diff.append("  Advancer: ").append(advancerResult.jobBlockState()).append("\n");
            }

            // Work remaining can be off by a small amount
            int workDiff = Math.abs(tickerResult.workRemaining() - advancerResult.workRemaining());
            if (workDiff > 5) { // Allow small variance
                equivalent = false;
                diff.append("Work remaining differs significantly:\n");
                diff.append("  Ticker:   ").append(tickerResult.workRemaining()).append("\n");
                diff.append("  Advancer: ").append(advancerResult.workRemaining()).append("\n");
            }

            return new EquivalenceComparison(
                    equivalent,
                    equivalent ? "Results are equivalent" : diff.toString()
            );
        }
    }

    /**
     * Creates a ticker setup for a job definition.
     * This is the same as JobIntegrationTest.createTicker but exposed for reuse.
     */
    public static TickerSetup createTickerSetup(JobDefinition definition) {
        return createTickerSetup(definition, null);
    }

    public static TickerSetup createTickerSetup(JobDefinition definition, @Nullable TestWorldAccess worldAccess) {
        TestWorkStatusHandle workStatusHandle = new TestWorkStatusHandle();
        ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory = TestInventory.sized(6);

        workStatusHandle.setJobBlockState(
                IntegrationTestWorld.DEFAULT_WORKSPOT_POS,
                State.fresh().setWorkLeft(definition.workRequiredAtStates().getOrDefault(0, 0))
        );

        TestWorldInteraction worldInteraction = worldAccess != null
                ? TestWorldInteraction.forDefinition(
                        definition, inventory, workStatusHandle, () -> null,
                        definition.specialRulesAtStates(), worldAccess)
                : TestWorldInteraction.forDefinition(
                        definition, inventory, workStatusHandle, () -> null,
                        definition.specialRulesAtStates());

        TestTickerDependencies deps = new TestTickerDependencies(
                definition,
                inventory,
                workStatusHandle,
                worldInteraction
        );

        DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> ticker =
                new DeclarativeJobTicker<>(
                        ImmutableList.of(),
                        com.google.common.collect.ImmutableMap.of(),
                        definition.jobId().rootId(),
                        definition.maxState()
                );

        return new TickerSetup(ticker, deps, inventory, worldInteraction, workStatusHandle);
    }

    /**
     * Runs the ticker for a specified number of ticks.
     */
    public static void runTicker(TickerSetup setup, int ticks) {
        for (int i = 0; i < ticks; i++) {
            setup.deps.syncJournalWithInventory();
            setup.ticker.tick(setup.deps, (msg, args) -> {});
        }
    }

    /**
     * Captures the result after running a simulation.
     */
    public static SimulationResult captureResult(TickerSetup setup) {
        return SimulationResult.capture(
                setup.inventory,
                setup.worldInteraction,
                setup.workStatusHandle,
                IntegrationTestWorld.DEFAULT_WORKSPOT_POS
        );
    }

    /**
     * Extended TickerSetup that includes workStatusHandle for result capture.
     */
    public record TickerSetup(
            DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> ticker,
            TestTickerDependencies deps,
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
            TestWorldInteraction worldInteraction,
            TestWorkStatusHandle workStatusHandle
    ) {}

    /**
     * Creates an advancer setup using the same infrastructure as ticker.
     * The advancer simulates time warp by computing important ticks and running
     * the ticker at each of those ticks instead of every tick.
     */
    public static AdvancerSetup createAdvancerSetup(JobDefinition definition) {
        return createAdvancerSetup(definition, null);
    }

    public static AdvancerSetup createAdvancerSetup(JobDefinition definition, @Nullable TestWorldAccess worldAccess) {
        TestWorkStatusHandle workStatusHandle = new TestWorkStatusHandle();
        ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory = TestInventory.sized(6);

        workStatusHandle.setJobBlockState(
                IntegrationTestWorld.DEFAULT_WORKSPOT_POS,
                State.fresh().setWorkLeft(definition.workRequiredAtStates().getOrDefault(0, 0))
        );

        TestWorldInteraction worldInteraction = worldAccess != null
                ? TestWorldInteraction.forDefinition(
                        definition, inventory, workStatusHandle, () -> null,
                        definition.specialRulesAtStates(), worldAccess)
                : TestWorldInteraction.forDefinition(
                        definition, inventory, workStatusHandle, () -> null,
                        definition.specialRulesAtStates());

        TestTickerDependencies deps = new TestTickerDependencies(
                definition,
                inventory,
                workStatusHandle,
                worldInteraction
        );

        DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> ticker =
                new DeclarativeJobTicker<>(
                        ImmutableList.of(),
                        com.google.common.collect.ImmutableMap.of(),
                        definition.jobId().rootId(),
                        definition.maxState()
                );

        return new AdvancerSetup(ticker, deps, inventory, worldInteraction, workStatusHandle, definition);
    }

    /**
     * Runs the advancer simulation for a specified number of ticks.
     * Uses ImportantTicks to compute which ticks matter and only runs the ticker at those.
     */
    public static void runAdvancer(AdvancerSetup setup, int totalTicks) {
        // Compute the important ticks for this job
        ca.bradj.questown.town.entity.ImportantTicks.Config config =
                new ca.bradj.questown.town.entity.ImportantTicks.Config(0); // No downtime for tests

        // Create a test Work implementation that uses the job definition
        ca.bradj.questown.town.AbstractAdvanceTime.Work work = new ca.bradj.questown.town.AbstractAdvanceTime.Work() {
            @Override
            public void recomputeNow() {}

            @Override
            public ca.bradj.questown.jobs.JobID getRandomFinishableWork(
                    ca.bradj.questown.jobs.JobID jobID,
                    ca.bradj.questown.jobs.Signals.DayTime dayTime,
                    long ticksElapsed
            ) {
                return setup.definition.jobId();
            }

            @Override
            public long getTotalDuration(ca.bradj.questown.jobs.JobID jobID, ca.bradj.questown.core.VillagerUUID vuid) {
                // For test equivalence: use a short cycle duration that allows multiple cycles
                // within the test's tick budget. The ticker runs every tick, so the advancer
                // needs to run enough cycles to complete the job.
                // Use a fraction of totalTicks to ensure at least one full cycle.
                int ingredientStates = setup.definition.ingredientsRequiredAtStates().size();
                int workStates = setup.definition.workRequiredAtStates().size();
                int timeStates = setup.definition.timeRequiredAtStates().size();
                int totalStates = Math.max(1, ingredientStates + workStates + timeStates);
                // Duration should allow completion within the test budget
                return Math.max(1, totalTicks / (totalStates + 1));
            }

            @Override
            public int getWarpTicksPerCycle(ca.bradj.questown.jobs.JobID jobID, ca.bradj.questown.core.VillagerUUID vuid) {
                // Each state transition needs at least one tick
                // Add extra ticks to handle work states that need multiple ticks
                int ingredientSteps = setup.definition.ingredientsRequiredAtStates().size();
                int toolSteps = setup.definition.toolsRequiredAtStates().size();
                int workRequired = setup.definition.workRequiredAtStates().values().stream()
                        .mapToInt(v -> v).sum();
                // Work states need one tick per work unit
                return Math.max(5, ingredientSteps + toolSteps + workRequired + 3);
            }
        };

        // Get important ticks
        ca.bradj.questown.town.entity.ImportantTicks.Result result =
                ca.bradj.questown.town.entity.ImportantTicks.forVillager(
                        work,
                        ca.bradj.questown.core.VillagerUUID.from(java.util.UUID.randomUUID()),
                        setup.definition.jobId(),
                        job -> false,
                        config,
                        totalTicks,
                        0
                );

        // Run the ticker at each important tick
        for (ca.bradj.questown.town.Warper.Tick tick : result.ticks()) {
            setup.deps.syncJournalWithInventory();
            setup.ticker.tick(setup.deps, (msg, args) -> {});
        }
    }

    /**
     * Captures the result after running an advancer simulation.
     */
    public static SimulationResult captureAdvancerResult(AdvancerSetup setup) {
        return SimulationResult.capture(
                setup.inventory,
                setup.worldInteraction,
                setup.workStatusHandle,
                IntegrationTestWorld.DEFAULT_WORKSPOT_POS
        );
    }

    /**
     * Advancer setup - uses ticker internally but runs at important ticks only.
     */
    public record AdvancerSetup(
            DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> ticker,
            TestTickerDependencies deps,
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
            TestWorldInteraction worldInteraction,
            TestWorkStatusHandle workStatusHandle,
            JobDefinition definition
    ) {}
}
