package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.items.KnowledgeMetaItem;
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
 * Guards how {@code tryGiveItems} fires {@code postExtractHook} (which runs the
 * {@code EXTRACTING_PRODUCT} special rules) per result type.
 *
 * <p>Background: the eating jobs used to extract an {@code EffectMetaItem} and
 * lost their {@code HUNGER_FILL} rule because the effect branch skipped the hook
 * (the {@code eating/dine_at_time} starvation bug). That whole branch is gone —
 * eating jobs now produce <em>empty</em> results, so extraction takes the
 * empty-stack path that has always fired the hook, and mood/hunger are applied
 * via special rules. These tests pin that contract.
 */
class ExtractHookFiresForMetaResultsTest {

    private static final Position WORK_SPOT = new Position(0, 0);
    private static final String KNOWLEDGE_MARKER = "test:knowledge";

    /** A {@link TestWorldInteraction} that recognises a knowledge marker and records hook calls. */
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
        protected boolean isInstanze(GathererJournalTest.TestItem item, Class<?> clazz) {
            return clazz == KnowledgeMetaItem.class && KNOWLEDGE_MARKER.equals(item.value);
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
        // once with a null item and the EXTRACTING_PRODUCT rules (HUNGER_FILL,
        // mood) run. This is what keeps dining villagers from starving.
        RecordingWorldInteraction twi = newWorldInteraction();

        twi.tryGiveItems(null, ImmutableList.of(), WORK_SPOT);

        Assertions.assertEquals(1, twi.postExtractCalls);
        Assertions.assertNull(twi.lastExtracted);
    }

    @Test
    void knowledgeResult_doesNotFirePostExtractHook_scopedToProductsOnly() {
        // Knowledge items are not products; the gatherer emits one per gather and
        // firing its extract rules would double-apply them (inflating warp loot).
        RecordingWorldInteraction twi = newWorldInteraction();

        twi.tryGiveItems(
                null,
                ImmutableList.of(new GathererJournalTest.TestItem(KNOWLEDGE_MARKER)),
                WORK_SPOT
        );

        Assertions.assertEquals(0, twi.postExtractCalls);
    }

    @Test
    void normalItemResult_firesPostExtractHookOncePerItem() {
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
