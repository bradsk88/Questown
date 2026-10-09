package ca.bradj.questown.town.entity;

import ca.bradj.questown.gui.ItemEconomicsData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Decision 2 (gannet top-need handoff, 2026-10-09, walked with the dev): the
 * top-need batch WALKS the ranked unmet-need queue to the first need with a
 * producer the town could unlock — a non-actionable #1 (no producer at all, or
 * all of its producers already unlocked) is skipped, not dead-end. Run 14 is
 * the motivating cell: #1 {@code oak_wood} (x4) had no unlockable producer and
 * silenced a lower need that did.
 * <p>
 * Semantics as decided, one for each:
 * <ol>
 *   <li>walk-skip past non-actionable needs;</li>
 *   <li>stop at the first *actionable* need (an in-flight unlock quest for its
 *       producer is checked by the caller and ends the phase — the walk never
 *       reaches past an in-flight one to post for a lower need);</li>
 *   <li>item-queue-only scope — room needs and {@code NoRequest} rows are gated
 *       out of the item store at registration ({@code GateReason.supplyItem()}),
 *       so they never appear here.</li>
 * </ol>
 */
class TownQuestsTopNeedWalkTest {

    private static ItemEconomicsData need(String key, int times) {
        return new ItemEconomicsData(key, times);
    }

    /** Which roots declare which products (the omniscient producer map, keyed by need key). */
    private static Map<String, Set<String>> producers() {
        return Map.of(
                "minecraft:oak_wood", Set.of("gatherer"),
                "minecraft:coal", Set.of("miner"),
                "minecraft:cod", Set.of("cook")
        );
    }

    private static java.util.function.Function<String, Set<String>> rootsFor() {
        return key -> producers().getOrDefault(key, Set.of());
    }

    @Test
    void skipsFirstNeedThatHasNoProducerAtAll() {
        TownQuests.TopNeedChoice choice = TownQuests.chooseTopNeed(
                List.of(need("minecraft:stone", 10), need("minecraft:coal", 4)),
                rootsFor(),
                Set.of("hunter")
        );
        assertEquals("minecraft:coal", choice.need().ingredientKey());
        assertEquals("miner", choice.producer());
    }

    @Test
    void skipsFirstNeedWhoseProducersAreAllUnlocked() {
        TownQuests.TopNeedChoice choice = TownQuests.chooseTopNeed(
                List.of(need("minecraft:oak_wood", 10), need("minecraft:coal", 4)),
                rootsFor(),
                Set.of("gatherer")
        );
        assertEquals("minecraft:coal", choice.need().ingredientKey());
        assertEquals("miner", choice.producer());
    }

    @Test
    void stopsAtTheFirstActionableEvenWhenLowerNeedsAreAlsoActionable() {
        TownQuests.TopNeedChoice choice = TownQuests.chooseTopNeed(
                List.of(need("minecraft:oak_wood", 10), need("minecraft:coal", 9), need("minecraft:cod", 8)),
                rootsFor(),
                Set.of()
        );
        assertEquals("minecraft:oak_wood", choice.need().ingredientKey());
        assertEquals("gatherer", choice.producer());
    }

    @Test
    void nothingActionableYieldsANullChoice() {
        TownQuests.TopNeedChoice choice = TownQuests.chooseTopNeed(
                List.of(need("minecraft:stone", 10), need("minecraft:cod", 4)),
                rootsFor(),
                Set.of("cook")
        );
        assertNull(choice.need());
        assertNull(choice.producer());
    }

    @Test
    void skipsNeedsThatNameNoKey() {
        TownQuests.TopNeedChoice choice = TownQuests.chooseTopNeed(
                List.of(need(null, 10), need("minecraft:coal", 4)),
                rootsFor(),
                Set.of()
        );
        assertEquals("minecraft:coal", choice.need().ingredientKey());
        assertEquals("miner", choice.producer());
    }

    @Test
    void chooseProducerRootWithNoStuckRootStillExcludesUnlockedRoots() {
        // The top-need batch has no stuck root (its needs are town-wide), so a
        // null stuckRoot excludes nothing — but the not-yet-unlocked filter
        // still applies.
        assertEquals(
                "miner",
                TownQuests.chooseProducerRoot(Set.of("miner", "baker"), null, Set.of("baker"))
        );
        assertNull(TownQuests.chooseProducerRoot(Set.of("miner"), null, Set.of("miner")));
    }
}
