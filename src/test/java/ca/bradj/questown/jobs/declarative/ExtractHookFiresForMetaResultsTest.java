package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.TestInventory;
import ca.bradj.questown.logic.MonoPredicateCollection;
import ca.bradj.questown.town.Claim;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Guards that {@code tryGiveItems} fires {@code postExtractHook} (which runs the
 * {@code EXTRACTING_PRODUCT} special rules) once per extraction.
 *
 * <p>History: the meta-item result types (effect, knowledge) used to be branched on
 * inside {@code tryGiveItems}, and the effect branch skipped the hook — the
 * {@code eating/dine_at_time} starvation bug. Both meta-item concepts have since been
 * deleted (ADR-0003, ADR-0004): eating jobs produce empty results and the explorer
 * scouts via a rule reading the extracted map, so every result now flows through the
 * single normal/empty path that always fires the hook. These tests pin that.
 */
class ExtractHookFiresForMetaResultsTest {

    private static final Position WORK_SPOT = new Position(0, 0);

    /** A {@link TestWorldInteraction} that records every {@code postExtractHook} call. */
    private static class RecordingWorldInteraction extends TestWorldInteraction {
        int postExtractCalls;
        GathererJournalTest.TestItem lastExtracted;

        RecordingWorldInteraction(
                ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
                TestWorkStatusHandle workStatuses,
                Supplier<Claim> claim
        ) {
            super(
                    1, // maxState; EXTRACTING_PRODUCT lives at the max state
                    ImmutableMap.<Integer, MonoPredicateCollection<GathererJournalTest.TestItem>>of(),
                    ImmutableMap.of(),
                    ImmutableMap.<Integer, MonoPredicateCollection<GathererJournalTest.TestItem>>of(),
                    ImmutableMap.of(),
                    ImmutableMap.of(),
                    ImmutableList.of(),
                    inventory,
                    workStatuses,
                    claim,
                    // A non-empty rule list at the max state makes postExtractHook delegate.
                    ImmutableMap.of(1, ImmutableList.of("test:some_extract_rule"))
            );
        }

        @Override
        protected Boolean postExtractHook(
                Boolean town,
                Collection<String> rules,
                Void inputs,
                Position position,
                GathererJournalTest.TestItem extractedItem
        ) {
            postExtractCalls++;
            lastExtracted = extractedItem;
            return town;
        }
    }

    private RecordingWorldInteraction newWorldInteraction() {
        return new RecordingWorldInteraction(
                TestInventory.sized(6),
                new TestWorkStatusHandle(),
                () -> null
        );
    }

    @Test
    void emptyResult_firesPostExtractHookOnce_regressionForDineAtTimeStarvation() {
        // The path the eating jobs now take: no product item, so the hook fires
        // once with a null item and the EXTRACTING_PRODUCT rules run.
        RecordingWorldInteraction twi = newWorldInteraction();

        twi.tryGiveItems(null, ImmutableList.of(), WORK_SPOT);

        Assertions.assertEquals(1, twi.postExtractCalls);
        Assertions.assertNull(twi.lastExtracted);
    }

    @Test
    void normalItemResult_firesPostExtractHookOncePerItem() {
        // Every product (including the explorer's gatherer map, which the scout rule
        // reads at this hook) now flows through this single path.
        RecordingWorldInteraction twi = newWorldInteraction();

        twi.tryGiveItems(
                null,
                ImmutableList.of(new GathererJournalTest.TestItem("minecraft:wheat")),
                WORK_SPOT
        );

        Assertions.assertEquals(1, twi.postExtractCalls);
        Assertions.assertEquals("minecraft:wheat", twi.lastExtracted.value);
    }
}
