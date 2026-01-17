package ca.bradj.questown.jobs.scenarios;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Unified scenario tests that verify the same scenarios work correctly for
 * BOTH the warp system (AbstractAdvanceTime) AND the realtime system (JobLogic.tick).
 * <p>
 * The goal is to build a safety net before extracting common code between
 * advanceTime and JobLogic.tick, ensuring both systems produce consistent outcomes.
 * <p>
 * Each test runs a scenario against both executors and verifies that:
 * 1. Both complete without errors
 * 2. Both produce similar outcomes (within acceptable tolerance)
 * <p>
 * Scenarios are defined declaratively using {@link VillagerWorkScenario}.
 */
class UnifiedScenarioTest {

    // Tolerance for comparing warp vs realtime results
    // Warp typically produces ~20% fewer items than realtime
    private static final double TOLERANCE = 0.25;

    // --- Scenario Definitions ---

    static Stream<VillagerWorkScenario> crafterScenarios() {
        return Stream.of(
                VillagerWorkScenario.builder("stick_production")
                        .description("Crafter converts saplings to sticks")
                        .initialJob("crafter", "stick")
                        .initialStatus(ProductionStatus.IDLE)
                        .containerItems(Map.of("sapling", 10))
                        .expectedContainerChanges(Map.of("sapling", -10, "stick", 20))
                        .expectedCyclesCompleted(10)
                        .build(),

                VillagerWorkScenario.builder("bowl_production")
                        .description("Crafter converts planks to bowls")
                        .initialJob("crafter", "bowl")
                        .initialStatus(ProductionStatus.IDLE)
                        .containerItems(Map.of("plank", 20))
                        // Bowl recipe: 2 planks -> 3 bowls
                        .expectedContainerChanges(Map.of("plank", -20, "bowl", 30))
                        .expectedCyclesCompleted(10)
                        .build(),

                VillagerWorkScenario.builder("job_switching")
                        .description("Crafter switches between stick and bowl jobs")
                        .initialJob("crafter", "stick")
                        .initialStatus(ProductionStatus.IDLE)
                        .containerItems(Map.of("sapling", 5, "plank", 10))
                        // Should produce both sticks and bowls
                        .expectedCyclesCompleted(10)
                        .build()
        );
    }

    static Stream<VillagerWorkScenario> gathererScenarios() {
        return Stream.of(
                VillagerWorkScenario.builder("basic_gathering")
                        .description("Gatherer consumes food and returns with loot")
                        .initialJob("gatherer", "forest_gatherer")
                        .initialStatus(ProductionStatus.IDLE)
                        .containerItems(Map.of("carrot", 10))
                        .expectedFoodConsumed(3)  // ~3 trips
                        .expectedCyclesCompleted(3)
                        .build(),

                VillagerWorkScenario.builder("mid_warp_gathering")
                        .description("Gatherer is mid-trip when warp starts")
                        .initialJob("gatherer", "forest_gatherer")
                        .initialStatus(ProductionStatus.WAITING_FOR_TIMED_STATE)
                        .containerItems(Map.of("carrot", 10))
                        .timerTicks(2000)  // Halfway through a 4000-tick trip
                        .expectedFoodConsumed(3)
                        .expectedCyclesCompleted(3)
                        .build()
        );
    }

    static Stream<VillagerWorkScenario> bakerScenarios() {
        return Stream.of(
                VillagerWorkScenario.builder("bread_production")
                        .description("Baker converts wheat + coal to bread")
                        .initialJob("baker", "bread")
                        .initialStatus(ProductionStatus.IDLE)
                        .containerItems(Map.of("wheat", 9, "coal", 3))
                        // 3 wheat + 1 coal -> 3 bread (per cycle)
                        .expectedContainerChanges(Map.of("wheat", -9, "coal", -3, "bread", 9))
                        .expectedCyclesCompleted(3)
                        .build()
        );
    }

    static Stream<VillagerWorkScenario> edgeCaseScenarios() {
        return Stream.of(
                VillagerWorkScenario.builder("no_supplies")
                        .description("Villager has no supplies to work with")
                        .initialJob("crafter", "stick")
                        .initialStatus(ProductionStatus.NO_SUPPLIES)
                        .containerItems(Map.of())  // Empty container
                        .expectedCyclesCompleted(0)
                        .build(),

                VillagerWorkScenario.builder("no_jobsite")
                        .description("Villager has no job site available")
                        .initialJob("cook", "simple_furnace_food")
                        .initialStatus(ProductionStatus.NO_JOBSITE)
                        .containerItems(Map.of("raw_beef", 5))
                        .expectedCyclesCompleted(0)
                        .build(),

                VillagerWorkScenario.builder("item_recovery")
                        .description("Inserted items recovered on NO_SUPPLIES")
                        .initialJob("cook", "simple_furnace_food")
                        .initialStatus(ProductionStatus.EXTRACTING_PRODUCT)
                        .villagerItems(Map.of("raw_beef", 1))  // Item was inserted
                        .containerItems(Map.of())  // No more supplies
                        .expectedCyclesCompleted(0)
                        .build()
        );
    }

    static Stream<VillagerWorkScenario> allScenarios() {
        return Stream.of(
                crafterScenarios(),
                gathererScenarios(),
                bakerScenarios(),
                edgeCaseScenarios()
        ).flatMap(s -> s);
    }

    // --- Test Methods ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("allScenarios")
    void scenario_shouldProduceSimilarResults_warpAndRealtime(VillagerWorkScenario scenario) {
        ScenarioExecutor warpExecutor = new WarpScenarioExecutor();
        ScenarioExecutor realtimeExecutor = new RealtimeScenarioExecutor();

        long ticks = 10_000;

        ScenarioExecutor.ExecutionResult warpResult = warpExecutor.execute(scenario, ticks);
        ScenarioExecutor.ExecutionResult realtimeResult = realtimeExecutor.execute(scenario, ticks);

        assertSimilarOutcomes(warpResult, realtimeResult, scenario);
    }

    // --- Test Infrastructure Verification ---

    @Test
    void testTownState_canCountItems() {
        TestTownState town = TestTownState.withContainer(Map.of("sapling", 5, "plank", 3));

        Assertions.assertEquals(5, town.countItems("sapling"));
        Assertions.assertEquals(3, town.countItems("plank"));
        Assertions.assertEquals(0, town.countItems("stick"));
    }

    @Test
    void testTownState_emptyIsEmpty() {
        TestTownState town = TestTownState.empty();

        Assertions.assertTrue(town.containers.isEmpty());
        Assertions.assertTrue(town.villagers.isEmpty());
    }

    @Test
    void villagerWorkScenario_builderWorks() {
        VillagerWorkScenario scenario = VillagerWorkScenario.builder("test")
                .description("Test scenario")
                .initialJob("crafter", "stick")
                .containerItems(Map.of("sapling", 10))
                .expectedCyclesCompleted(5)
                .build();

        Assertions.assertEquals("test", scenario.name());
        Assertions.assertEquals(new JobID("crafter", "stick"), scenario.initialJob());
        Assertions.assertEquals(10, scenario.containerItems().get("sapling"));
        Assertions.assertEquals(5, scenario.expectedCyclesCompleted());
    }

    // --- Assertion Helpers ---

    private void assertSimilarOutcomes(
            ScenarioExecutor.ExecutionResult warpResult,
            ScenarioExecutor.ExecutionResult realtimeResult,
            VillagerWorkScenario scenario
    ) {
        // Cycles completed should be within tolerance
        int warpCycles = warpResult.cyclesCompleted();
        int realtimeCycles = realtimeResult.cyclesCompleted();

        if (realtimeCycles > 0) {
            double ratio = (double) warpCycles / realtimeCycles;
            Assertions.assertTrue(
                    ratio >= (1.0 - TOLERANCE) && ratio <= (1.0 + TOLERANCE),
                    String.format(
                            "Scenario '%s': Cycles differ too much. Warp=%d, Realtime=%d, Ratio=%.2f",
                            scenario.name(), warpCycles, realtimeCycles, ratio
                    )
            );
        } else if (scenario.expectedCyclesCompleted() == 0) {
            // Both should complete 0 cycles
            Assertions.assertEquals(0, warpCycles,
                    "Scenario '" + scenario.name() + "': Expected 0 cycles, but warp completed " + warpCycles);
        }

        // Food consumed should be similar (for gatherer scenarios)
        if (scenario.expectedFoodConsumed() > 0) {
            int warpFood = warpResult.foodConsumed();
            int realtimeFood = realtimeResult.foodConsumed();

            if (realtimeFood > 0) {
                double ratio = (double) warpFood / realtimeFood;
                Assertions.assertTrue(
                        ratio >= (1.0 - TOLERANCE) && ratio <= (1.0 + TOLERANCE),
                        String.format(
                                "Scenario '%s': Food consumed differs too much. Warp=%d, Realtime=%d",
                                scenario.name(), warpFood, realtimeFood
                        )
                );
            }
        }
    }
}
