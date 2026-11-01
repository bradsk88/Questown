package ca.bradj.questown.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-core test for {@link RoomRecipes#upgradeCandidates}, the room-upgrade candidate selection
 * extracted from {@code TownQuests.getUpgradeRecipe} (Forge-free seam; see the
 * {@code Work.isUnlockedInitially} precedent). The ids are fed in as data because a
 * codec-constructed (data-driven) recipe's {@code RoomRecipe.getId()} is null on 1.20.2+, where the
 * canonical id is the recipe holder's key.
 *
 * <p>Selection semantics: a candidate is a recipe whose ingredient groups *cover* every group of the
 * from-recipe (each from-group must contain some to-group: the upgrade refines/extends the recipe's
 * inputs), and a perfect overlap is the recipe itself, not an upgrade. Every non-overlapping
 * candidate is returned; the caller picks one (at random).
 */
class RoomRecipesUpgradeCandidatesTest {

    private static final List<String> FURNACE = List.of("minecraft:furnace");
    private static final List<String> CHEST = List.of("minecraft:chest");
    private static final List<String> TORCH = List.of("minecraft:torch");
    private static final List<String> COAL = List.of("minecraft:coal");

    @Test
    void perfectOverlap_isExcluded() {
        List<List<String>> from = List.of(FURNACE, CHEST);
        Map<String, List<List<String>>> all = Map.of(
                "questown:kitchen_stocked", List.of(FURNACE, CHEST),
                "questown:unrelated", List.of(TORCH));

        assertTrue(RoomRecipes.upgradeCandidates(from, all).isEmpty());
    }

    @Test
    void recipeCoveringEveryFromGroup_isACandidate() {
        // from = kitchen_small [furnace]; a to-recipe that keeps the furnace
        // group and adds a chest group covers it.
        List<List<String>> from = List.of(FURNACE);
        Map<String, List<List<String>>> all = Map.of(
                "questown:kitchen_stocked", List.of(FURNACE, CHEST));

        assertEquals(
                List.of("questown:kitchen_stocked"),
                RoomRecipes.upgradeCandidates(from, all));
    }

    @Test
    void everyCoveringRecipe_isReturned() {
        List<List<String>> from = List.of(FURNACE, CHEST);
        Map<String, List<List<String>>> all = Map.of(
                "questown:a", List.of(FURNACE, CHEST, TORCH),
                "questown:b", List.of(FURNACE, CHEST, CHEST),
                "questown:miss_furnace_only", List.of(FURNACE),
                "questown:unrelated", List.of(TORCH));

        assertEquals(
                List.of("questown:a", "questown:b"),
                RoomRecipes.upgradeCandidates(from, all).stream().sorted().toList());
    }

    @Test
    void recipeMissingACoverForAFromGroup_isNotACandidate() {
        // from has a chest group; a to-recipe without one can't cover it.
        List<List<String>> from = List.of(FURNACE, CHEST);
        Map<String, List<List<String>>> all = Map.of(
                "questown:kitchen_small", List.of(FURNACE));

        assertTrue(RoomRecipes.upgradeCandidates(from, all).isEmpty());
    }

    @Test
    void groupLevelContainment_oneCoarseFromGroupCoveredByFinerToGroup() {
        // from group [coal, stick] is covered by the finer to group [coal] —
        // containment is per-group (containsAll), not item-equality.
        List<List<String>> from = List.of(List.of("minecraft:coal", "minecraft:stick"));
        Map<String, List<List<String>>> all = Map.of(
                "questown:fuel_only", List.of(COAL));

        assertEquals(
                List.of("questown:fuel_only"),
                RoomRecipes.upgradeCandidates(from, all));
    }

    @Test
    void emptyUniverse_returnsEmpty() {
        List<List<String>> from = List.of(FURNACE);

        assertTrue(RoomRecipes.upgradeCandidates(from, Map.of()).isEmpty());
    }
}
