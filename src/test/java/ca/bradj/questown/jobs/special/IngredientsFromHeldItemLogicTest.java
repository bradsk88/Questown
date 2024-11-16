package ca.bradj.questown.jobs.special;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static ca.bradj.questown.jobs.special.IngredientsFromHeldItemLogic.ingredientsExistForHoldableItems;

class IngredientsFromHeldItemLogicTest {

    @Test
    void ingredientsExistForHoldableItems_shouldReturnTrue_IfChestContainsHoldableItem_AndOtherChestsContainsIngredient() {
        boolean result = ingredientsExistForHoldableItems(
                ImmutableList.of(() -> ImmutableList.of("holdable")),
                ImmutableList.of(() -> ImmutableList.of("ingredient")),
                "holdable"::equals,
                item -> "ingredient",
                (item, request) -> request.equals(item)
        );
        Assertions.assertTrue(result);
    }
    @Test
    void ingredientsExistForHoldableItems_shouldReturnFalse_IfChestContainsHoldableItem_ButOtherChestsDontHaveIngredient() {
        boolean result = ingredientsExistForHoldableItems(
                ImmutableList.of(() -> ImmutableList.of("holdable")),
                ImmutableList.of(() -> ImmutableList.of("unwanted item")),
                "holdable"::equals,
                item -> "ingredient",
                (item, request) -> request.equals(item)
        );
        Assertions.assertFalse(result);
    }
    @Test
    void ingredientsExistForHoldableItems_shouldReturnFalse_IfChestDoesNotContainHoldableItem() {
        boolean result = ingredientsExistForHoldableItems(
                ImmutableList.of(() -> ImmutableList.of("unwanted item")),
                ImmutableList.of(() -> ImmutableList.of("ingredient")),
                "holdable"::equals,
                item -> "ingredient",
                (item, request) -> request.equals(item)
        );
        Assertions.assertFalse(result);
    }
}