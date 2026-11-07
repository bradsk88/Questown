package ca.bradj.questown.town.entity;

import ca.bradj.questown.gui.ItemEconomicsData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The top-need batch walks the ranked needs to the first one a job not yet unlocked could make; others are skipped. */
class TownQuestsTopNeedWalkTest {

    private static ItemEconomicsData need(String key, int times) {
        return new ItemEconomicsData(key, times);
    }

    /** Which roots make which items. */
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
        // No stuck root to exclude, but unlocked producers still are.
        assertEquals(
                "miner",
                TownQuests.chooseProducerRoot(Set.of("miner", "baker"), null, Set.of("baker"))
        );
        assertNull(TownQuests.chooseProducerRoot(Set.of("miner"), null, Set.of("miner")));
    }
}
