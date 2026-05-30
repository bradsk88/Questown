package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.items.EffectMetaItem;
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
 * Regression for the {@code eating/dine_at_time} bar-C blocker: a villager that
 * dines at a table extracts an {@link EffectMetaItem} (the eating-mood effect)
 * as its only product. {@code tryGiveItems} routed effect/knowledge results
 * through {@code withEffectApplied}/{@code withKnowledge} and silently skipped
 * {@code postExtractHook}, so the {@code EXTRACTING_PRODUCT} special rules — in
 * particular {@code HUNGER_FILL} — never ran and the villager starved.
 *
 * <p>These tests assert the extract hook fires once per extracted result
 * regardless of the result item's type.
 */
class ExtractHookFiresForMetaResultsTest {

    private static final Position WORK_SPOT = new Position(0, 0);
    private static final String EFFECT_MARKER = "test:effect";
    private static final String KNOWLEDGE_MARKER = "test:knowledge";

    /**
     * A {@link TestWorldInteraction} that recognises designated marker items as
     * effect/knowledge meta-items and records every {@code postExtractHook} call.
     */
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
                    ImmutableList.of(), // results: passed directly to tryGiveItems in each test
                    inventory,
                    workStatuses,
                    claim,
                    // A non-empty rule list at the max state makes postExtractHook delegate.
                    ImmutableMap.of(1, ImmutableList.of("test:some_extract_rule"))
            );
        }

        @Override
        protected boolean isInstanze(GathererJournalTest.TestItem item, Class<?> clazz) {
            if (clazz == EffectMetaItem.class) {
                return EFFECT_MARKER.equals(item.value);
            }
            if (clazz == KnowledgeMetaItem.class) {
                return KNOWLEDGE_MARKER.equals(item.value);
            }
            return false;
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
    void effectResult_stillFiresPostExtractHook_regressionForDineAtTimeStarvation() {
        RecordingWorldInteraction twi = newWorldInteraction();

        twi.tryGiveItems(
                null,
                ImmutableList.of(new GathererJournalTest.TestItem(EFFECT_MARKER)),
                WORK_SPOT
        );

        Assertions.assertEquals(
                1, twi.postExtractCalls,
                "Extracting an EffectMetaItem result must still run EXTRACTING_PRODUCT rules (e.g. HUNGER_FILL)"
        );
    }

    @Test
    void knowledgeResult_doesNotFirePostExtractHook_scopedToEffectsOnly() {
        // The hook is fired only for effect results. Firing it for knowledge results
        // double-applies the gatherer's EXTRACTING_PRODUCT rules (it emits a knowledge
        // item per gather), which inflated warp loot yields — so knowledge is left as-is.
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
