package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.InsertIntoSlotSpecialRule;
import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.TakeFromSlotSpecialRule;
import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobDefinition;
import ca.bradj.questown.world.TestWorldAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Ticker-vs-advancer equivalence for the cook-shaped baker job family
 * (baker/extract + baker/bread + baker/fuel). Same shape as
 * {@link CookEquivalenceTest}: the world is a 3-slot container (the bread
 * oven) with a wheat->bread smelt recipe, so insert/extract slot rules run
 * against real slot state on both lanes.
 */
class BakerEquivalenceTest {

    private static final String JOBS_PATH = "data/questown/questown_jobs/";
    private static final BlockPos WORKSPOT_BLOCKPOS = new BlockPos(0, 0, 0);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        SpecialRulesRegistry.resetForTesting();
        SpecialRulesRegistry.registerSpecialRule(
                new ResourceLocation("questown", "insert_into_slot_0"),
                new InsertIntoSlotSpecialRule(0));
        SpecialRulesRegistry.registerSpecialRule(
                new ResourceLocation("questown", "insert_into_slot_1"),
                new InsertIntoSlotSpecialRule(1));
        SpecialRulesRegistry.registerSpecialRule(
                new ResourceLocation("questown", "take_from_slot_2"),
                new TakeFromSlotSpecialRule(2));
        SpecialRulesRegistry.finalizeForServer();
    }

    private static TestWorldAccess createOvenWorld() {
        return new TestWorldAccess()
                .withContainer(WORKSPOT_BLOCKPOS, 3)
                .withSmeltRecipe(Items.WHEAT, Items.BREAD)
                .withContainerSlot(WORKSPOT_BLOCKPOS, 1, new ItemStack(Items.COAL));
    }

    @Test
    void baker_bread_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "baker_bread.json");
        TestWorldAccess world = createOvenWorld();

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:wheat"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:wheat"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        TestWorldAccess world2 = createOvenWorld();
        EquivalenceTestFramework.AdvancerSetup advancerSetup =
                EquivalenceTestFramework.createAdvancerSetup(definition, world2);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:wheat"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:wheat"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 200);
        EquivalenceTestFramework.SimulationResult advancerResult =
                EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
        // Equivalence alone is hollow: both sides stalling together still "equivalents".
        // Assert the job actually completed on BOTH sides.
        Assertions.assertTrue(
                tickerResult.productExtracted() && advancerResult.productExtracted(),
                "both sides must actually complete the bread cycle, not just agree to idle");
        Assertions.assertEquals(Items.WHEAT, world.getSlotContents(WORKSPOT_BLOCKPOS, 0).getItem(),
                "Wheat should have been inserted into oven slot 0");
    }

    @Test
    void baker_fuel_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "baker_fuel.json");

        TestWorldAccess world = createOvenWorld();
        world.withContainerSlot(WORKSPOT_BLOCKPOS, 1, new ItemStack(Items.AIR));

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:coal"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:coal"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        TestWorldAccess world2 = createOvenWorld();
        world2.withContainerSlot(WORKSPOT_BLOCKPOS, 1, new ItemStack(Items.AIR));
        EquivalenceTestFramework.AdvancerSetup advancerSetup =
                EquivalenceTestFramework.createAdvancerSetup(definition, world2);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:coal"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:coal"));

        EquivalenceTestFramework.runAdvancer(advancerSetup, 200);
        EquivalenceTestFramework.SimulationResult advancerResult =
                EquivalenceTestFramework.captureAdvancerResult(advancerSetup);

        EquivalenceTestFramework.EquivalenceComparison comparison =
                EquivalenceTestFramework.EquivalenceComparison.compare(tickerResult, advancerResult);

        Assertions.assertTrue(comparison.equivalent(),
                "Ticker and Advancer should produce equivalent results.\n" + comparison.diffMessage());
        Assertions.assertTrue(
                tickerResult.productExtracted() && advancerResult.productExtracted(),
                "both sides must actually complete the fuel insertion, not just agree to idle");
    }

    /**
     * Work-only start-gate regression (mirror of cook_extract's): a state 0 that
     * declares ONLY work (0.1 floors to int 0, no ingredients, no tools) must
     * still start on a bread-ready oven and extract with an EMPTY inventory.
     */
    @Test
    void baker_extract_workOnly_state0_emptyInventory_shouldExtractProduct() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "baker_extract.json");
        Assertions.assertTrue(definition.toolsRequiredAtStates().isEmpty(),
                "precondition: no tools declared (work-only state 0)");
        Assertions.assertTrue(definition.workRequiredAtStates().containsKey(0),
                "precondition: work declared at state 0");

        TestWorldAccess world = createOvenWorld();
        world.withContainerSlot(WORKSPOT_BLOCKPOS, 2, new ItemStack(Items.BREAD));

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        // inventory stays EMPTY - no tool, no ingredient

        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        Assertions.assertTrue(tickerResult.productExtracted(),
                "a work-only state-0 oven must complete its work and extract; got " + tickerResult);
        Assertions.assertEquals("minecraft:bread", tickerResult.inventoryItems().get(0),
                "the extracted product must land in the empty inventory; got " + tickerResult);
    }
}
