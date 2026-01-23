package ca.bradj.questown.jobs.integration;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.TestWorldInteraction;
import ca.bradj.questown.jobs.declarative.ValidatedInventoryHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

/**
 * Integration tests that load real JSON job definitions and verify jobs complete correctly.
 * Tests use DeclarativeJobTicker for comprehensive integration testing.
 */
class JobIntegrationTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";

    /**
     * Run the DeclarativeJobTicker for a specified number of ticks.
     */
    private void runTicks(
            TestTickerDependencies deps,
            DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> ticker,
            int ticks
    ) {
        for (int i = 0; i < ticks; i++) {
            deps.syncJournalWithInventory();
            ticker.tick(deps, (msg, args) -> {});
        }
    }

    /**
     * Create a DeclarativeJobTicker and TestTickerDependencies for a job definition.
     */
    private record TickerSetup(
            DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> ticker,
            TestTickerDependencies deps,
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory
    ) {}

    private TickerSetup createTicker(JobDefinition definition) {
        TestWorkStatusHandle workStatusHandle = new TestWorkStatusHandle();
        ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory = TestInventory.sized(6);

        // Initialize work state at the job block position
        workStatusHandle.setJobBlockState(
                IntegrationTestWorld.DEFAULT_WORKSPOT_POS,
                State.fresh().setWorkLeft(definition.workRequiredAtStates().getOrDefault(0, 0))
        );

        TestWorldInteraction worldInteraction = TestWorldInteraction.forDefinition(
                definition,
                inventory,
                workStatusHandle,
                () -> null
        );

        TestTickerDependencies deps = new TestTickerDependencies(
                definition,
                inventory,
                workStatusHandle,
                worldInteraction
        );

        return new TickerSetup(newTicker(definition), deps, inventory);
    }

    private static @NotNull DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> newTicker(JobDefinition definition) {
        ImmutableList<String> specialGlobalRules = ImmutableList.of();
        ImmutableMap<Object, Collection<String>> specialRules = ImmutableMap.of();
        DeclarativeJobTicker<Position, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, Void, String> ticker =
                new DeclarativeJobTicker<>(
                        specialGlobalRules,
                        specialRules,
                        definition.jobId().rootId(),
                        definition.maxState()
                );
        return ticker;
    }

    // ========== JSON Loader Tests ==========

    @Test
    void testJobLoader_shouldParseAllWorkStates() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "baker_bread.json");

        Assertions.assertEquals("minecraft:wheat", definition.ingredientsRequiredAtStates().get(0));
        Assertions.assertEquals(Integer.valueOf(2), definition.ingredientQtyRequiredAtStates().get(0));
        Assertions.assertEquals("#minecraft:coals", definition.ingredientsRequiredAtStates().get(1));
        Assertions.assertEquals(Integer.valueOf(1), definition.ingredientQtyRequiredAtStates().get(1));
        Assertions.assertEquals(Integer.valueOf(1000), definition.timeRequiredAtStates().get(2));
    }

    @Test
    void crafter_stick_shouldLoadCorrectly() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_stick.json");

        Assertions.assertEquals(new JobID("crafter", "stick"), definition.jobId());
        Assertions.assertEquals(2, definition.maxState());
        Assertions.assertEquals("#minecraft:saplings", definition.ingredientsRequiredAtStates().get(0));
        Assertions.assertEquals(Integer.valueOf(1), definition.ingredientQtyRequiredAtStates().get(0));
        Assertions.assertEquals(Integer.valueOf(5), definition.workRequiredAtStates().get(1));
        Assertions.assertEquals("minecraft:stick", definition.result());
    }

    @Test
    void crafter_bowl_shouldLoadCorrectly() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_bowl.json");

        Assertions.assertEquals(new JobID("crafter", "bowl"), definition.jobId());
        Assertions.assertEquals(2, definition.maxState());
        Assertions.assertEquals("#minecraft:planks", definition.ingredientsRequiredAtStates().get(0));
        Assertions.assertEquals(Integer.valueOf(2), definition.ingredientQtyRequiredAtStates().get(0));
        Assertions.assertEquals(Integer.valueOf(10), definition.workRequiredAtStates().get(1));
        Assertions.assertEquals("minecraft:bowl", definition.result());
    }

    // ========== DeclarativeJobTicker Tests ==========

    @Test
    void declarativeJobTicker_crafter_stick_shouldCompleteJob() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_stick.json");
        TickerSetup setup = createTicker(definition);

        // Give the villager a sapling
        setup.inventory.set(0, new GathererJournalTest.TestItem("#minecraft:saplings"));

        runTicks(setup.deps, setup.ticker, 50);

        // Check that the product was extracted (world interaction tracks this)
        Assertions.assertTrue(
                setup.deps.getWorldInteraction() instanceof TestWorldInteraction,
                "WorldInteraction should be TestWorldInteraction"
        );
        TestWorldInteraction twi = (TestWorldInteraction) setup.deps.getWorldInteraction();
        Assertions.assertTrue(twi.wasExtracted(), "Product should be extracted");

        // Check inventory has the result
        boolean hasStick = setup.inventory.getItems().stream()
                .anyMatch(item -> item.value.equals("minecraft:stick"));
        Assertions.assertTrue(hasStick, "Should have stick. Got: " + setup.inventory.getItems());
    }

    @Test
    void declarativeJobTicker_crafter_bowl_shouldCompleteJob() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_bowl.json");
        TickerSetup setup = createTicker(definition);

        // Give the villager two planks
        setup.inventory.set(0, new GathererJournalTest.TestItem("#minecraft:planks"));
        setup.inventory.set(1, new GathererJournalTest.TestItem("#minecraft:planks"));

        runTicks(setup.deps, setup.ticker, 100);

        // Check that the product was extracted
        TestWorldInteraction twi = (TestWorldInteraction) setup.deps.getWorldInteraction();
        Assertions.assertTrue(twi.wasExtracted(), "Product should be extracted");

        // Check inventory has the result
        boolean hasBowl = setup.inventory.getItems().stream()
                .anyMatch(item -> item.value.equals("minecraft:bowl"));
        Assertions.assertTrue(hasBowl, "Should have bowl. Got: " + setup.inventory.getItems());
    }
}
