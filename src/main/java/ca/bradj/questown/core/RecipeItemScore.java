package ca.bradj.questown.core;

import java.util.Collection;

public class RecipeItemScore {

    public static int requiresCraftingTable(
            Collection<? extends Resource> resources,
            boolean boosted
    ) {
        int score = canCraftInFourGrid(resources, boosted);
        // Extra cost due to crafting table requirement
        return score + 10;
    }

    public static int canCraftInFourGrid(
            Collection<? extends Resource> resources,
            boolean boosted
    ) {
        int score = resources.stream()
                .mapToInt(Resource::calculateValue)
                .sum() / 4;
        if (boosted) {
            score = score / 7;
        }
        return score;
    }
}
