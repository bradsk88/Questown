package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.workstatus.State;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.AbstractMap;

/**
 * Integration tests for multi-villager warp scenarios.
 * These tests verify that when multiple villagers warp simultaneously,
 * their states remain independent and ticks are processed in correct order.
 */
class MultiVillagerWarpTest {

    /**
     * Represents shared town container storage that villagers interact with
     */
    static class SharedContainers {
        List<String> items = new ArrayList<>();

        void addItem(String item) {
            items.add(item);
        }

        String takeItem(String itemType) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).equals(itemType)) {
                    return items.remove(i);
                }
            }
            return null; // Not found
        }

        boolean hasItem(String itemType) {
            return items.contains(itemType);
        }
    }

    /**
     * Represents a villager's state during warp simulation
     */
    static class VillagerWarpState {
        final int villagerIndex;
        List<String> inventory = new ArrayList<>();
        State workState = State.fresh();
        long timer = 0;
        String jobType = "generic"; // e.g., "producer", "consumer"
        String producesItem = null; // What this villager produces
        String requiresItem = null; // What this villager needs as supply

        VillagerWarpState(int index) {
            this.villagerIndex = index;
        }
    }

    /**
     * Represents a warp step for a specific villager at a specific tick
     */
    record WarpStep(long tick, int villagerIndex, ProductionStatus status) {}

    /**
     * Simulates processing a warp step for a villager (isolated, no shared state)
     */
    private void processStep(VillagerWarpState state, ProductionStatus status) {
        processStep(state, status, null);
    }

    /**
     * Simulates processing a warp step for a villager with shared container access
     */
    private void processStep(VillagerWarpState state, ProductionStatus status, SharedContainers containers) {
        if (status.equals(ProductionStatus.COLLECTING_SUPPLIES)) {
            if (containers != null && state.requiresItem != null) {
                // Try to collect specific required item from shared containers
                String taken = containers.takeItem(state.requiresItem);
                if (taken != null) {
                    state.inventory.add(taken);
                }
                // If required item not available, villager gets nothing (would be NO_SUPPLIES in real code)
            } else {
                // Default behavior for tests without shared containers
                state.inventory.add("supply_" + state.villagerIndex);
            }
        } else if (status.equals(ProductionStatus.DROPPING_LOOT)) {
            if (containers != null && state.producesItem != null) {
                // Drop produced items to shared containers
                for (int i = state.inventory.size() - 1; i >= 0; i--) {
                    String item = state.inventory.get(i);
                    if (item.equals(state.producesItem)) {
                        state.inventory.remove(i);
                        containers.addItem(item);
                    }
                }
            } else {
                // Default behavior
                state.inventory.removeIf(s -> s.startsWith("result_"));
            }
        } else if (status.isWorkingOnProduction() && state.workState.hasWorkLeft()) {
            state.workState = state.workState.decrWork(10);
            // When work completes, consume supply and produce result
            if (!state.workState.hasWorkLeft() && state.producesItem != null) {
                if (state.requiresItem != null) {
                    state.inventory.remove(state.requiresItem);
                }
                state.inventory.add(state.producesItem);
            }
        }
    }

    @Test
    void multiVillager_shouldWarpIndependently() {
        // Two villagers with different initial states
        VillagerWarpState v0 = new VillagerWarpState(0);
        v0.inventory.add("item_v0");
        v0.workState = State.fresh().setWorkLeft(5);

        VillagerWarpState v1 = new VillagerWarpState(1);
        v1.inventory.add("item_v1");
        v1.workState = State.fresh().setWorkLeft(10);

        // Simulate warp steps - each villager works independently
        ProductionStatus workStatus = ProductionStatus.fromJobBlockStatus(0);
        processStep(v0, workStatus);
        processStep(v1, workStatus);

        // Each villager's work should decrease independently
        Assertions.assertEquals(4, v0.workState.workLeft(), "V0 work should decrease");
        Assertions.assertEquals(9, v1.workState.workLeft(), "V1 work should decrease");

        // Inventories should remain isolated
        Assertions.assertTrue(v0.inventory.contains("item_v0"));
        Assertions.assertFalse(v0.inventory.contains("item_v1"));
        Assertions.assertTrue(v1.inventory.contains("item_v1"));
        Assertions.assertFalse(v1.inventory.contains("item_v0"));
    }

    @Test
    void multiVillager_collectSupplies_shouldNotInterfereWithEachOther() {
        VillagerWarpState v0 = new VillagerWarpState(0);
        VillagerWarpState v1 = new VillagerWarpState(1);

        // Both villagers collect supplies
        processStep(v0, ProductionStatus.COLLECTING_SUPPLIES);
        processStep(v1, ProductionStatus.COLLECTING_SUPPLIES);

        // Each should have their own supply
        Assertions.assertEquals(1, v0.inventory.size());
        Assertions.assertEquals("supply_0", v0.inventory.get(0));
        Assertions.assertEquals(1, v1.inventory.size());
        Assertions.assertEquals("supply_1", v1.inventory.get(0));
    }

    @Test
    void multiVillager_ticksShouldBeOrderedCorrectly() {
        // Simulate tick generation like TownFlagState.advanceTime()
        List<WarpStep> allSteps = new ArrayList<>();

        // Villager 0: ticks at 100, 200
        allSteps.add(new WarpStep(100, 0, ProductionStatus.COLLECTING_SUPPLIES));
        allSteps.add(new WarpStep(200, 0, ProductionStatus.fromJobBlockStatus(0)));

        // Villager 1: ticks at 150, 250
        allSteps.add(new WarpStep(150, 1, ProductionStatus.COLLECTING_SUPPLIES));
        allSteps.add(new WarpStep(250, 1, ProductionStatus.fromJobBlockStatus(0)));

        // Sort by tick (like warpSteps.sort in TownFlagState)
        allSteps.sort(Comparator.comparingLong(WarpStep::tick));

        // Verify order: 100, 150, 200, 250
        Assertions.assertEquals(100, allSteps.get(0).tick());
        Assertions.assertEquals(0, allSteps.get(0).villagerIndex());
        Assertions.assertEquals(150, allSteps.get(1).tick());
        Assertions.assertEquals(1, allSteps.get(1).villagerIndex());
        Assertions.assertEquals(200, allSteps.get(2).tick());
        Assertions.assertEquals(0, allSteps.get(2).villagerIndex());
        Assertions.assertEquals(250, allSteps.get(3).tick());
        Assertions.assertEquals(1, allSteps.get(3).villagerIndex());
    }

    @Test
    void multiVillager_processingInOrder_shouldMaintainStateCorrectly() {
        VillagerWarpState v0 = new VillagerWarpState(0);
        v0.workState = State.fresh().setWorkLeft(3);
        VillagerWarpState v1 = new VillagerWarpState(1);
        v1.workState = State.fresh().setWorkLeft(3);

        // Interleaved steps sorted by tick
        List<WarpStep> steps = List.of(
                new WarpStep(100, 0, ProductionStatus.COLLECTING_SUPPLIES),
                new WarpStep(150, 1, ProductionStatus.COLLECTING_SUPPLIES),
                new WarpStep(200, 0, ProductionStatus.fromJobBlockStatus(0)),
                new WarpStep(250, 1, ProductionStatus.fromJobBlockStatus(0)),
                new WarpStep(300, 0, ProductionStatus.DROPPING_LOOT),
                new WarpStep(350, 1, ProductionStatus.DROPPING_LOOT)
        );

        // Process in order
        for (WarpStep step : steps) {
            VillagerWarpState state = step.villagerIndex() == 0 ? v0 : v1;
            processStep(state, step.status());
        }

        // Both should have collected and processed
        Assertions.assertEquals(1, v0.inventory.size(), "V0 should have supply");
        Assertions.assertEquals(1, v1.inventory.size(), "V1 should have supply");
        Assertions.assertEquals(2, v0.workState.workLeft(), "V0 work should decrease");
        Assertions.assertEquals(2, v1.workState.workLeft(), "V1 work should decrease");
    }

    @Test
    void multiVillager_manyVillagers_shouldAllProgressIndependently() {
        int numVillagers = 5;
        List<VillagerWarpState> villagers = new ArrayList<>();
        for (int i = 0; i < numVillagers; i++) {
            VillagerWarpState v = new VillagerWarpState(i);
            v.workState = State.fresh().setWorkLeft(i + 1); // Each has different work
            villagers.add(v);
        }

        // Each villager works once
        ProductionStatus workStatus = ProductionStatus.fromJobBlockStatus(0);
        for (VillagerWarpState v : villagers) {
            processStep(v, workStatus);
        }

        // Verify each villager's work decreased independently
        for (int i = 0; i < numVillagers; i++) {
            int expectedWork = i; // Started with i+1, decreased by 1
            Assertions.assertEquals(
                    expectedWork,
                    villagers.get(i).workState.workLeft(),
                    "Villager " + i + " should have correct work remaining"
            );
        }
    }

    @Test
    void multiVillager_differentStatuses_shouldNotAffectEachOther() {
        VillagerWarpState v0 = new VillagerWarpState(0);
        v0.inventory.add("result_0"); // Has result to drop

        VillagerWarpState v1 = new VillagerWarpState(1);
        // V1 has nothing, just collecting

        // V0 drops loot while V1 collects
        processStep(v0, ProductionStatus.DROPPING_LOOT);
        processStep(v1, ProductionStatus.COLLECTING_SUPPLIES);

        // V0 dropped its result
        Assertions.assertTrue(v0.inventory.isEmpty(), "V0 should have dropped result");

        // V1 collected a supply
        Assertions.assertEquals(1, v1.inventory.size());
        Assertions.assertEquals("supply_1", v1.inventory.get(0));
    }

    // --- Producer-Consumer Chain Tests ---

    @Test
    void producerConsumer_consumerGetsProducerOutput_whenOrderedCorrectly() {
        // Scenario: Miller produces flour, Baker needs flour
        // Producer must drop before consumer can collect
        SharedContainers containers = new SharedContainers();

        // Miller (producer): wheat -> flour
        VillagerWarpState miller = new VillagerWarpState(0);
        miller.jobType = "miller";
        miller.requiresItem = "wheat";
        miller.producesItem = "flour";
        miller.inventory.add("wheat"); // Already collected wheat
        miller.workState = State.fresh().setWorkLeft(1);

        // Baker (consumer): flour -> bread
        VillagerWarpState baker = new VillagerWarpState(1);
        baker.jobType = "baker";
        baker.requiresItem = "flour";
        baker.producesItem = "bread";
        baker.workState = State.fresh().setWorkLeft(1);

        // Warp steps in correct order:
        // 1. Miller works (produces flour)
        // 2. Miller drops flour to container
        // 3. Baker collects flour from container
        // 4. Baker works (produces bread)
        List<WarpStep> steps = List.of(
                new WarpStep(100, 0, ProductionStatus.fromJobBlockStatus(0)), // Miller works
                new WarpStep(200, 0, ProductionStatus.DROPPING_LOOT),          // Miller drops flour
                new WarpStep(300, 1, ProductionStatus.COLLECTING_SUPPLIES),    // Baker collects flour
                new WarpStep(400, 1, ProductionStatus.fromJobBlockStatus(0))   // Baker works
        );

        // Process steps in order
        for (WarpStep step : steps) {
            VillagerWarpState villager = step.villagerIndex() == 0 ? miller : baker;
            processStep(villager, step.status(), containers);
        }

        // Verify: Miller's work complete, flour was dropped
        Assertions.assertEquals(0, miller.workState.workLeft(), "Miller should have completed work");
        Assertions.assertTrue(miller.inventory.isEmpty(), "Miller should have dropped flour");

        // Verify: Baker collected flour and produced bread
        Assertions.assertEquals(0, baker.workState.workLeft(), "Baker should have completed work");
        Assertions.assertTrue(baker.inventory.contains("bread"), "Baker should have produced bread");
        Assertions.assertFalse(baker.inventory.contains("flour"), "Baker should have consumed flour");

        // Verify: Container should be empty (flour was taken by baker)
        Assertions.assertFalse(containers.hasItem("flour"), "Container should not have flour");
    }

    @Test
    void producerConsumer_consumerGetsNothing_whenProducerHasNotDroppedYet() {
        // Scenario: Consumer tries to collect BEFORE producer has dropped
        SharedContainers containers = new SharedContainers();

        // Miller (producer)
        VillagerWarpState miller = new VillagerWarpState(0);
        miller.producesItem = "flour";
        miller.inventory.add("flour"); // Has flour but hasn't dropped yet

        // Baker (consumer)
        VillagerWarpState baker = new VillagerWarpState(1);
        baker.requiresItem = "flour";

        // Baker tries to collect before miller drops
        processStep(baker, ProductionStatus.COLLECTING_SUPPLIES, containers);

        // Baker should have gotten nothing (no flour in containers yet)
        Assertions.assertTrue(baker.inventory.isEmpty(), "Baker should not have collected anything");

        // Now miller drops
        processStep(miller, ProductionStatus.DROPPING_LOOT, containers);

        // Flour should now be in container
        Assertions.assertTrue(containers.hasItem("flour"), "Container should have flour after miller drops");
    }

    @Test
    void producerConsumer_multipleProducersOneConsumer_consumerGetsFirstAvailable() {
        // Two millers produce flour, one baker consumes
        SharedContainers containers = new SharedContainers();

        VillagerWarpState miller1 = new VillagerWarpState(0);
        miller1.producesItem = "flour";
        miller1.inventory.add("flour");

        VillagerWarpState miller2 = new VillagerWarpState(1);
        miller2.producesItem = "flour";
        miller2.inventory.add("flour");

        VillagerWarpState baker = new VillagerWarpState(2);
        baker.requiresItem = "flour";

        // Both millers drop
        processStep(miller1, ProductionStatus.DROPPING_LOOT, containers);
        processStep(miller2, ProductionStatus.DROPPING_LOOT, containers);

        // Container should have 2 flour
        Assertions.assertEquals(2, containers.items.stream().filter("flour"::equals).count());

        // Baker collects one
        processStep(baker, ProductionStatus.COLLECTING_SUPPLIES, containers);

        // Baker should have 1 flour, container should have 1 flour remaining
        Assertions.assertEquals(1, baker.inventory.size());
        Assertions.assertEquals("flour", baker.inventory.get(0));
        Assertions.assertEquals(1, containers.items.stream().filter("flour"::equals).count());
    }

    @Test
    void producerConsumer_chainOfThree_shouldPropagateCorrectly() {
        // Farmer -> Miller -> Baker chain
        // Farmer: (raw) -> wheat
        // Miller: wheat -> flour
        // Baker: flour -> bread
        SharedContainers containers = new SharedContainers();

        VillagerWarpState farmer = new VillagerWarpState(0);
        farmer.producesItem = "wheat";
        farmer.workState = State.fresh().setWorkLeft(1);

        VillagerWarpState miller = new VillagerWarpState(1);
        miller.requiresItem = "wheat";
        miller.producesItem = "flour";
        miller.workState = State.fresh().setWorkLeft(1);

        VillagerWarpState baker = new VillagerWarpState(2);
        baker.requiresItem = "flour";
        baker.producesItem = "bread";
        baker.workState = State.fresh().setWorkLeft(1);

        // Execute chain in order
        List<WarpStep> steps = List.of(
                // Farmer produces and drops wheat
                new WarpStep(100, 0, ProductionStatus.fromJobBlockStatus(0)),
                new WarpStep(200, 0, ProductionStatus.DROPPING_LOOT),
                // Miller collects wheat, produces flour, drops it
                new WarpStep(300, 1, ProductionStatus.COLLECTING_SUPPLIES),
                new WarpStep(400, 1, ProductionStatus.fromJobBlockStatus(0)),
                new WarpStep(500, 1, ProductionStatus.DROPPING_LOOT),
                // Baker collects flour, produces bread
                new WarpStep(600, 2, ProductionStatus.COLLECTING_SUPPLIES),
                new WarpStep(700, 2, ProductionStatus.fromJobBlockStatus(0))
        );

        for (WarpStep step : steps) {
            VillagerWarpState villager = switch (step.villagerIndex()) {
                case 0 -> farmer;
                case 1 -> miller;
                case 2 -> baker;
                default -> throw new IllegalStateException();
            };
            processStep(villager, step.status(), containers);
        }

        // Verify end state
        Assertions.assertTrue(farmer.inventory.isEmpty(), "Farmer should have dropped wheat");
        Assertions.assertTrue(miller.inventory.isEmpty(), "Miller should have dropped flour");
        Assertions.assertTrue(baker.inventory.contains("bread"), "Baker should have bread");

        // Containers should be empty (all items consumed through chain)
        Assertions.assertTrue(containers.items.isEmpty(), "All items should be consumed through chain");
    }
}
