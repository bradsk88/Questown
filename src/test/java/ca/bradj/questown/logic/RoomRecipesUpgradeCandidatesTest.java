package ca.bradj.questown.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link RoomRecipes#upgradeCandidates}: a recipe that covers every ingredient group of the other is an upgrade; the same recipe is not. */
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
        // Keeping the furnace and adding a chest covers it.
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
        List<List<String>> from = List.of(FURNACE, CHEST);
        Map<String, List<List<String>>> all = Map.of(
                "questown:kitchen_small", List.of(FURNACE));

        assertTrue(RoomRecipes.upgradeCandidates(from, all).isEmpty());
    }

    @Test
    void groupLevelContainment_oneCoarseFromGroupCoveredByFinerToGroup() {
        // Coverage is per group: [coal] covers [coal, stick].
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
