package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.InsertIntoSlotSpecialRule;
import ca.bradj.questown.integration.SpecialRulesRegistry;
import ca.bradj.questown.integration.TakeFromSlotSpecialRule;
import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobDefinition;
import ca.bradj.questown.world.TestWorldAccess;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

class CookEquivalenceTest {

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

    private static TestWorldAccess createFurnaceWorld() {
        return new TestWorldAccess()
                .withContainer(WORKSPOT_BLOCKPOS, 3)
                .withSmeltRecipe(Items.BEEF, Items.COOKED_BEEF)
                .withSmeltRecipe(Items.COD, Items.COOKED_COD);
    }

    @Test
    void cook_simple_furnace_food_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "cook_simple_furnace_food.json");
        TestWorldAccess world = createFurnaceWorld();

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:beef"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:beef"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        TestWorldAccess world2 = createFurnaceWorld();
        EquivalenceTestFramework.AdvancerSetup advancerSetup =
                EquivalenceTestFramework.createAdvancerSetup(definition, world2);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:beef"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:beef"));

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
                "both sides must actually complete the extract, not just agree to idle");

        Assertions.assertTrue(
                tickerResult.productExtracted() || advancerResult.productExtracted()
                        || world.getSlotContents(WORKSPOT_BLOCKPOS, 0).getItem() == Items.BEEF
                        || world2.getSlotContents(WORKSPOT_BLOCKPOS, 0).getItem() == Items.BEEF,
                "Beef should have been inserted into furnace slot 0 or product extracted");
    }

    @Test
    void cook_fish_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "cook_fish.json");
        TestWorldAccess world = createFurnaceWorld();

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:cod"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:cod"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        TestWorldAccess world2 = createFurnaceWorld();
        EquivalenceTestFramework.AdvancerSetup advancerSetup =
                EquivalenceTestFramework.createAdvancerSetup(definition, world2);
        advancerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:cod"));
        advancerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:cod"));

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
                "both sides must actually complete the extract, not just agree to idle");
    }

    @Test
    void cook_fuel_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "cook_fuel.json");
        TestWorldAccess world = createFurnaceWorld();

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        tickerSetup.inventory().set(0, new GathererJournalTest.TestItem("minecraft:coal"));
        tickerSetup.inventory().set(1, new GathererJournalTest.TestItem("minecraft:coal"));

        EquivalenceTestFramework.runTicker(tickerSetup, 200);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        TestWorldAccess world2 = createFurnaceWorld();
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
        // Equivalence alone is hollow: both sides stalling together still "equivalents".
        // Assert the job actually completed on BOTH sides.
        Assertions.assertTrue(
                tickerResult.productExtracted() && advancerResult.productExtracted(),
                "both sides must actually complete the extract, not just agree to idle");
    }

    /**
     * Work-only start-gate regression (cook/stick stall): a state 0 that declares ONLY work
     * (cook_extract's 0.1 floors to int 0, no ingredients, no tools) must still start on a
     * fresh block and extract its product with an EMPTY inventory. Before the fix, the
     * fresh block is invisible to the work bucket AND excluded from itemless work
     * (workLeft()==0), so the cook idles in NO_JOBSITE.
     */
    @Test
    void cook_extract_workOnly_state0_emptyInventory_shouldExtractProduct() {
        JobDefinition definition = workOnlyCookExtract();
        Assertions.assertTrue(definition.toolsRequiredAtStates().isEmpty(),
                "precondition: no tools declared (work-only state 0)");
        Assertions.assertTrue(definition.workRequiredAtStates().containsKey(0),
                "precondition: work declared at state 0");

        TestWorldAccess world = createFurnaceWorld();
        world.withContainerSlot(WORKSPOT_BLOCKPOS, 2, new ItemStack(Items.COOKED_BEEF));

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        // inventory stays EMPTY - no tool, no ingredient

        EquivalenceTestFramework.runTicker(tickerSetup, 100);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        Assertions.assertTrue(tickerResult.productExtracted(),
                "a work-only state-0 block must complete its work and extract; got " + tickerResult);
        Assertions.assertEquals("minecraft:cooked_beef", tickerResult.inventoryItems().get(0),
                "the extracted product must land in the empty inventory; got " + tickerResult);
    }

    /** cook_extract with the tools/note workaround stripped: pure work-only state 0. */
    private static JobDefinition workOnlyCookExtract() {
        try (InputStream is = CookEquivalenceTest.class.getClassLoader()
                .getResourceAsStream(JOBS_PATH + "cook_extract.json")) {
            JsonObject obj = new Gson().fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            JsonObject state0 = obj.getAsJsonArray("work_states").get(0).getAsJsonObject();
            state0.remove("tools");
            state0.remove("note");
            return TestJobLoader.parseJobDefinition(obj);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load cook_extract.json", e);
        }
    }

    @Test
    void cook_extract_tickerAndAdvancer_shouldProduceEquivalentResults() {
        JobDefinition definition = TestJobLoader.loadFromFile(JOBS_PATH + "cook_extract.json");
        TestWorldAccess world = createFurnaceWorld();
        world.withContainerSlot(WORKSPOT_BLOCKPOS, 2, new ItemStack(Items.COOKED_BEEF));

        EquivalenceTestFramework.TickerSetup tickerSetup =
                EquivalenceTestFramework.createTickerSetup(definition, world);
        // No held stick: cook/extract no longer uses one, and a villager holding an item no state
        // uses drops it before more work (2026-10-21), which this harness's store cannot accept.

        EquivalenceTestFramework.runTicker(tickerSetup, 50);
        EquivalenceTestFramework.SimulationResult tickerResult =
                EquivalenceTestFramework.captureResult(tickerSetup);

        TestWorldAccess world2 = createFurnaceWorld();
        world2.withContainerSlot(WORKSPOT_BLOCKPOS, 2, new ItemStack(Items.COOKED_BEEF));

        EquivalenceTestFramework.AdvancerSetup advancerSetup =
                EquivalenceTestFramework.createAdvancerSetup(definition, world2);

        EquivalenceTestFramework.runAdvancer(advancerSetup, 50);
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
                "both sides must actually complete the extract, not just agree to idle");
    }
}
