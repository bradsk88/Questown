package ca.bradj.questown.core;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RecipeCalculatorTest {

    @Test
    void torch() {

        int score = RecipeItemScore.canCraftInFourGrid(
                CommonRecipes.TORCH_INGREDIENTS, false
        );
        Assertions.assertEquals(17, score);
    }

    @Test
    void crafting_table() {
        int score = RecipeItemScore.canCraftInFourGrid(
                CommonRecipes.CRAFTING_TABLE, false
        );
        Assertions.assertEquals(22, score);
    }

    @Test
    void bed() {
        int score = RecipeItemScore.requiresCraftingTable(ImmutableList.of(
                new MinedResource("wool", Rarity.MEDIUM),
                new MinedResource("wool", Rarity.MEDIUM),
                new MinedResource("wool", Rarity.MEDIUM),
                new CraftedResource("planks", 4, ImmutableList.of(
                        new MinedResource("wood", Rarity.COMMON))
                ),
                new CraftedResource("planks", 4, ImmutableList.of(
                        new MinedResource("wood", Rarity.COMMON))
                ),
                new CraftedResource("planks", 4, ImmutableList.of(
                        new MinedResource("wood", Rarity.COMMON))
                )
        ), true);
        Assertions.assertEquals(23, score);
    }

    @Test
    void chest() {
        int score = RecipeItemScore.requiresCraftingTable(
                CommonRecipes.CHEST, false
        );
        Assertions.assertEquals(54, score);
    }

    @Test
    void furnace() {
        int score = RecipeItemScore.requiresCraftingTable(
                CommonRecipes.FURNACE, false
        );
        Assertions.assertEquals(30, score);
    }

    @Test
    void sign() {
        int score = RecipeItemScore.requiresCraftingTable(
                CommonRecipes.SIGN, false
        );
        Assertions.assertEquals(31, score);
    }

    @Test
    void bookshelf() {
        int score = RecipeItemScore.requiresCraftingTable(
                CommonRecipes.BOOKSHELF, false
        );
        Assertions.assertEquals(222, score);
    }

    @Test
    void ench_table() {
        int score = RecipeItemScore.requiresCraftingTable(
                CommonRecipes.ENCH_TABLE, false
        );
        Assertions.assertEquals(289, score);
    }

    @Test
    void brew_stand() {
        int score = RecipeItemScore.requiresCraftingTable(
                CommonRecipes.BREW_STAND, false
        );
        Assertions.assertEquals(217, score);
    }

    @Test
    void lantern() {
        int score = RecipeItemScore.requiresCraftingTable(
                CommonRecipes.LANTERN, false
        );
        Assertions.assertEquals(48, score);
    }
}