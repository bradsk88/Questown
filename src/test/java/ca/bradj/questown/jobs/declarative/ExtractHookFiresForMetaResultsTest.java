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

/** Every extraction fires {@code postExtractHook} once, including empty results. */
class ExtractHookFiresForMetaResultsTest {

    private static final Position WORK_SPOT = new Position(0, 0);

    private static class RecordingWorldInteraction extends TestWorldInteraction {
        int postExtractCalls;
        GathererJournalTest.TestItem lastExtracted;

        RecordingWorldInteraction(
                ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
                TestWorkStatusHandle workStatuses,
                Supplier<Claim> claim
        ) {
            super(
                    1,
                    ImmutableMap.<Integer, MonoPredicateCollection<GathererJournalTest.TestItem>>of(),
                    ImmutableMap.of(),
                    ImmutableMap.<Integer, MonoPredicateCollection<GathererJournalTest.TestItem>>of(),
                    ImmutableMap.of(),
                    ImmutableMap.of(),
                    ImmutableList.of(),
                    inventory,
                    workStatuses,
                    claim,
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
        // Eating: no product, so the hook fires once with null.
        RecordingWorldInteraction twi = newWorldInteraction();

        twi.tryGiveItems(null, ImmutableList.of(), WORK_SPOT);

        Assertions.assertEquals(1, twi.postExtractCalls);
        Assertions.assertNull(twi.lastExtracted);
    }

    @Test
    void normalItemResult_firesPostExtractHookOncePerItem() {
        // Every product, including the explorer's map, goes through this one path.
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
