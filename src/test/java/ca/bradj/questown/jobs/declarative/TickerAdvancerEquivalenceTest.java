package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying that DeclarativeJobTicker and AdvanceTime produce equivalent results.
 * These tests run the same job scenario through both systems and compare outcomes.
 *
 * Per the spec (ADVANCE_TIME_DECOUPLING_SPEC.md), equivalence criteria are:
 * - Final job block state matches
 * - Villager inventory contains same items (order may differ)
 * - Products extracted match
 * - Villager journal status matches (idle, working, etc.)
 * - Town containers contain the same "result" items (can be off by one or two)
 */
class TickerAdvancerEquivalenceTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";

    // ========== Crafter Job Equivalence Tests ==========

    @Test
    void crafter_stick_ticker_shouldCaptureResult() {
        // This test validates the equivalence framework captures ticker results correctly.
        // Once advancer is wired up, we'll compare both results.

        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_stick.json");
        EquivalenceTestFramework.TickerSetup setup = EquivalenceTestFramework.createTickerSetup(definition);

        // Give the villager a sapling
        setup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:saplings"));

        // Run ticker
        EquivalenceTestFramework.runTicker(setup, 50);

        // Capture result
        EquivalenceTestFramework.SimulationResult result = EquivalenceTestFramework.captureResult(setup);

        // Validate capture works
        Assertions.assertTrue(result.productExtracted(), "Product should be extracted");
        Assertions.assertTrue(
                result.inventoryItems().stream().anyMatch(s -> s.equals("minecraft:stick")),
                "Should have stick in inventory. Got: " + result.inventoryItems()
        );
    }

    @Test
    void crafter_bowl_ticker_shouldCaptureResult() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_bowl.json");
        EquivalenceTestFramework.TickerSetup setup = EquivalenceTestFramework.createTickerSetup(definition);

        // Give the villager two planks
        setup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:planks"));
        setup.inventory().set(1, new GathererJournalTest.TestItem("#minecraft:planks"));

        // Run ticker
        EquivalenceTestFramework.runTicker(setup, 100);

        // Capture result
        EquivalenceTestFramework.SimulationResult result = EquivalenceTestFramework.captureResult(setup);

        // Validate capture works
        Assertions.assertTrue(result.productExtracted(), "Product should be extracted");
        Assertions.assertTrue(
                result.inventoryItems().stream().anyMatch(s -> s.equals("minecraft:bowl")),
                "Should have bowl in inventory. Got: " + result.inventoryItems()
        );
    }

    // ========== Crafter Equivalence Tests ==========
    // These tests compare ticker vs advancer results

    @Test
    void crafter_stick_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_stick.json");

        // Setup ticker
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:saplings"));

        // Run ticker
        EquivalenceTestFramework.runTicker(tickerSetup, 50);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        // Setup advancer with identical initial state
        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:saplings"));

        // Run advancer for equivalent duration
        EquivalenceTestFramework.runAdvancer(advancerSetup, 50);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        // Compare results
        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void crafter_bowl_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_bowl.json");

        // Setup ticker
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:planks"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("#minecraft:planks"));

        // Run ticker
        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        // Setup advancer with identical initial state
        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:planks"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("#minecraft:planks"));

        // Run advancer for equivalent duration
        EquivalenceTestFramework.runAdvancer(advancerSetup, 100);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        // Compare results
        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Baker Job Equivalence Tests ==========

    @Test
    void baker_bread_tickerAndAdvancer_shouldProduceEquivalentResults() {
        // Baker bread has: wheat(x2) -> coal -> time(1000) -> bread
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "baker_bread.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:wheat"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:wheat"));
        tickerSetup.inventory().set(2, new GathererJournalTest.TestItem("#minecraft:coals"));

        EquivalenceTestFramework.runTicker(tickerSetup, 2000);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        // Setup advancer with identical initial state
        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:wheat"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:wheat"));
        advancerSetup.inventory().set(2, new GathererJournalTest.TestItem("#minecraft:coals"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 2000);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Gatherer Job Equivalence Tests ==========

    @Test
    void gatherer_notool_short_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "gatherer_unmapped_notool_short.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#questown:villager_food"));

        EquivalenceTestFramework.runTicker(tickerSetup, 3000);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        // Setup advancer with identical initial state
        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#questown:villager_food"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 3000);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Crafter Planks Equivalence Test ==========

    @Test
    void crafter_planks_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_planks.json");

        // Setup ticker
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:logs"));

        // Run ticker - planks needs 20 work
        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        // Setup advancer with identical initial state
        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:logs"));

        // Run advancer for equivalent duration
        EquivalenceTestFramework.runAdvancer(advancerSetup, 100);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        // Compare results
        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Smelter Equivalence Test ==========

    @Test
    void smelter_process_ore_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "smelter_process_ore.json");

        // Setup ticker - needs iron_ore and a pickaxe
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:iron_ore"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("#questown:pickaxes"));

        // Run ticker - 20 work
        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        // Setup advancer with identical initial state
        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:iron_ore"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("#questown:pickaxes"));

        // Run advancer for equivalent duration
        EquivalenceTestFramework.runAdvancer(advancerSetup, 100);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        // Compare results
        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Soup Cook Equivalence Test ==========

    @Test
    void soup_cook_one_mushroom_stew_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "soup_cook_one_mushroom_stew.json");

        // Setup ticker - needs mushroom, shovel, and bowl
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#forge:mushrooms"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("#questown:shovels"));
        tickerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:bowl"));

        // Run ticker - 50 work
        EquivalenceTestFramework.runTicker(tickerSetup, 150);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        // Setup advancer with identical initial state
        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#forge:mushrooms"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("#questown:shovels"));
        advancerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:bowl"));

        // Run advancer for equivalent duration
        EquivalenceTestFramework.runAdvancer(advancerSetup, 150);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        // Compare results
        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Baker Stock Tests ==========

    @Test
    void baker_stock_wheat_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "baker_stock_wheat.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:wheat"));

        EquivalenceTestFramework.runTicker(tickerSetup, 50);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:wheat"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 50);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void baker_stock_coal_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "baker_stock_coal.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:coals"));

        EquivalenceTestFramework.runTicker(tickerSetup, 50);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#minecraft:coals"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 50);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Additional Crafter Tests ==========

    @Test
    void crafter_ladder_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_ladder.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:stick"));

        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:stick"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 100);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void crafter_paper_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_paper.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:sugar_cane"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:sugar_cane"));

        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:sugar_cane"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:sugar_cane"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 100);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void crafter_shears_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_shears.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:iron_ingot"));

        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:iron_ingot"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 100);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Additional Gatherer Test ==========

    @Test
    void gatherer_axe_med_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "gatherer_unmapped_axe_med.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#questown:villager_food"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("#questown:axes"));

        EquivalenceTestFramework.runTicker(tickerSetup, 6000);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#questown:villager_food"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("#questown:axes"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 6000);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== More Crafter Tests ==========

    @Test
    void crafter_fishing_rod_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_fishing_rod.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:string"));

        EquivalenceTestFramework.runTicker(tickerSetup, 150);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:string"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 150);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== More Soup Cook Tests ==========

    @Test
    void soup_cook_two_mushroom_stew_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "soup_cook_two_mushroom_stew.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:bowl"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:bowl"));
        tickerSetup.inventory().set(2, new GathererJournalTest.TestItem("#questown:shovels"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:bowl"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:bowl"));
        advancerSetup.inventory().set(2, new GathererJournalTest.TestItem("#questown:shovels"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 200);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void soup_cook_stock_bowls_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "soup_cook_stock_bowls.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:bowl"));

        EquivalenceTestFramework.runTicker(tickerSetup, 50);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:bowl"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 50);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void soup_cook_fill_big_pot_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "soup_cook_fill_big_pot.json");

        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("#forge:mushrooms"));

        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("#forge:mushrooms"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 100);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void crafter_fishing_station_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "crafter_fishing_station.json");

        // Needs 4 fishing rods
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:fishing_rod"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:fishing_rod"));
        tickerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:fishing_rod"));
        tickerSetup.inventory().set(3, new GathererJournalTest.TestItem("minecraft:fishing_rod"));

        // 200 work + some extra time
        EquivalenceTestFramework.runTicker(tickerSetup, 400);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:fishing_rod"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:fishing_rod"));
        advancerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:fishing_rod"));
        advancerSetup.inventory().set(3, new GathererJournalTest.TestItem("minecraft:fishing_rod"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 400);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    // ========== Blacksmith Job Equivalence Tests ==========

    @Test
    void blacksmith_iron_axe_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "blacksmith_iron_axe.json");

        // Needs 1 stick, 2 iron ingots
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:iron_ingot"));
        tickerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:iron_ingot"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:iron_ingot"));
        advancerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:iron_ingot"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 200);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void blacksmith_stone_pickaxe_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "blacksmith_stone_pickaxe.json");

        // Needs 1 stick, 2 cobblestone
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:cobblestone"));
        tickerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:cobblestone"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:cobblestone"));
        advancerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:cobblestone"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 200);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void blacksmith_diamond_hoe_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "blacksmith_diamond_hoe.json");

        // Needs 1 stick, 1 diamond
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:diamond"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:diamond"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 200);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }

    @Test
    void blacksmith_golden_axe_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "blacksmith_golden_axe.json");

        // Needs 1 stick, 2 gold ingots
        EquivalenceTestFramework.TickerSetup tickerSetup = EquivalenceTestFramework.createTickerSetup(definition);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:gold_ingot"));
        tickerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:gold_ingot"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult = EquivalenceTestFramework.captureResult(tickerSetup);

        EquivalenceTestFramework.AdvancerSetup advancerSetup = EquivalenceTestFramework.createAdvancerSetup(definition);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:stick"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:gold_ingot"));
        advancerSetup.inventory().set(2, new GathererJournalTest.TestItem("minecraft:gold_ingot"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 200);
        EquivalenceTestFramework.SimulationResult advancerResult = EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
    }
}
