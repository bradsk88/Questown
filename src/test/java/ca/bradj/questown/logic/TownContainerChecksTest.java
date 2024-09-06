package ca.bradj.questown.logic;

import ca.bradj.questown.jobs.Item;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Function;
import java.util.function.Predicate;

public class TownContainerChecksTest {

    public record TestItem(
            String name,
            int quantity
    ) implements Item<TestItem> {
        @Override
        public boolean isEmpty() {
            return quantity > 0;
        }

        @Override
        public boolean isFood() {
            return false;
        }

        @Override
        public TestItem shrink() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getShortName() {
            return name;
        }

        @Override
        public TestItem unit() {
            throw new UnsupportedOperationException();
        }
    }

    private static TestItem APPLE = new TestItem("apple", 1);
    private static TestItem ORANGE = new TestItem("orange", 1);

    Predicate<TestItem> isFruit = item -> ImmutableList.of(APPLE, ORANGE).stream()
                                                       .anyMatch(v -> v.name.equals(item.name));

    Function<String, IPredicateCollection<TestItem>> required = (item) ->  new IPredicateCollection<>() {
        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean test(TestItem testItem) {
            return testItem.name.equals(item);
        }
    };

    IPredicateCollection<TestItem> toolNotRequired = new IPredicateCollection<>() {
        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public boolean test(TestItem testItem) {
            throw new UnsupportedOperationException();
        }
    };

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenOneItemFound_AndOneRequired() {
        boolean result = TownContainerChecks.<TestItem>townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE).stream().filter(predicate).toList(),
                isFruit,
                1,
                false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenTwoSameItemsFound_AndOneRequired() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, APPLE).stream().filter(predicate).toList(),
                isFruit,
                1,
                false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenTwoDifferentItemsFound_AndOneRequired() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, ORANGE).stream().filter(predicate).toList(),
                isFruit,
                1,
                false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenTwoSameItemsFound_AndTwoRequired_AndDifferentAllowed() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, APPLE).stream().filter(predicate).toList(),
                isFruit,
                2,
                false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenTwoSameItemsFound_AndTwoRequired_AndMustBeSame() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, APPLE).stream().filter(predicate).toList(),
                isFruit,
                2,
                true
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenTwoDifferentItemsFound_AndTwoRequired_AndDifferentAllowed() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, ORANGE).stream().filter(predicate).toList(),
                isFruit,
                2,
                false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnFalse_WhenNoItemsFound_AndOneRequired() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.<TestItem>of().stream().filter(predicate).toList(),
                isFruit,
                1,
                false
        );
        Assertions.assertFalse(result);
    }

    @Test
    void townHasSupplies_ShouldReturnFalse_WhenTwoDifferentItemsFound_AndTwoRequired_AndMustBeSame() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, ORANGE).stream().filter(predicate).toList(),
                isFruit,
                2,
                true
        );
        Assertions.assertFalse(result);
    }

    @Test
    void townHasSupplies_ShouldReturnFalse_WhenThreeItemsFoundWithTwoSame_AndThreeRequired_AndMustBeSame() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, APPLE, ORANGE).stream().filter(predicate).toList(),
                isFruit,
                3,
                true
        );
        Assertions.assertFalse(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenThreeItemsFoundWithTwoSameAtBeginning_AndTwoRequired_AndMustBeSame() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, APPLE, ORANGE).stream().filter(predicate).toList(),
                isFruit,
                2,
                true
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenThreeItemsFoundWithTwoSameAtEnd_AndTwoRequired_AndMustBeSame() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(APPLE, ORANGE, ORANGE).stream().filter(predicate).toList(),
                isFruit,
                2,
                true
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenThreeItemsFound_AndTwoRequired_AndMustBeSame() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(ORANGE, ORANGE, ORANGE).stream().filter(predicate).toList(),
                isFruit,
                2,
                true
        );
        Assertions.assertTrue(result);
    }

    @Test
    void townHasSupplies_ShouldReturnTrue_WhenStackOfTwoItemsFound_AndTwoRequired_AndMustBeSame() {
        boolean result = TownContainerChecks.townHasSuppliesForStage(
                (predicate) -> ImmutableList.of(new TestItem("orange", 2)).stream().filter(predicate).toList(),
                isFruit,
                2,
                true
        );
        Assertions.assertTrue(result);
    }

    @Test
    void hasSupplies_ShouldReturnTrue_IfToolsRequiredAtState0_ButRoomIsAlreadyAtState1() {
        boolean result = TownContainerChecks.<TestItem, Integer>hasSupplies(
                () -> ImmutableSet.of(1), // A room exists at state 1
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, (TestItem item) -> true // Town has the needed ingredients for state 1
                ),
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, 1
                ),
                ImmutableMap.of(
                        0, required.apply("tool")
                        // 1: No additional tools required
                ),
                (predicate) -> ImmutableList.of(new TestItem("tool", 1), new TestItem("ingredient", 1)).stream()
                                            .filter(predicate).toList(),
                (state) -> ImmutableList.of(),
                (i) -> false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void hasSupplies_ShouldReturnTrue_IfToolsRequiredAtState0_AndRoomIsAtState0_AndTownHasTools() {
        boolean result = TownContainerChecks.<TestItem, Integer>hasSupplies(
                () -> ImmutableSet.of(0), // A room exists at state 0
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, (TestItem item) -> true // Town has the needed ingredients for state 1
                ),
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, 1
                ),
                ImmutableMap.of(
                        0, required.apply("tool")
                        // 1: No additional tools required
                ),
                (predicate) -> ImmutableList.of(new TestItem("tool", 1), new TestItem("ingredient", 1)).stream()
                                            .filter(predicate).toList(),
                (state) -> ImmutableList.of(),
                (i) -> false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void hasSupplies_ShouldReturnTrue_IfToolsRequiredAtState0_AndRoomIsAtState0_AndEntityHasTools() {
        boolean result = TownContainerChecks.hasSupplies(
                () -> ImmutableSet.of(0), // A room exists at state 0
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, (TestItem item) -> false // Town does not have the needed ingredients for state 1
                ),
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, 1
                ),
                ImmutableMap.of(
                        0, required.apply("tool")
                        // 1: No additional tools required
                ),
                (predicate) -> ImmutableList.of(new TestItem("ingredient", 1)).stream().filter(predicate).toList(),
                predicate -> ImmutableList.of(new TestItem("tool", 1)).stream().filter(predicate).toList(),
                (state) -> ImmutableList.of(),
                (i) -> false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void hasSupplies_ShouldReturnTrue_If_2_IngredientsRequiredAtState0_AndRoomIsAtState0_AndEntityHasOneAndTownHasOne() {
        boolean result = TownContainerChecks.hasSupplies(
                () -> ImmutableSet.of(0), // A room exists at state 0
                ImmutableMap.of(
                        0, required.apply("ingredient")
                ),
                ImmutableMap.of(
                        0, 2 // two ingredients required
                ),
                ImmutableMap.of(
                        // No tools required
                ),
                predicate -> ImmutableList.of(new TestItem("ingredient", 1)).stream().filter(predicate).toList(), // town
                predicate -> ImmutableList.of(new TestItem("ingredient", 1)).stream().filter(predicate).toList(), // inventory
                (state) -> ImmutableList.of(),
                (i) -> false
        );
        Assertions.assertTrue(result);
    }

    @Test
    void hasSupplies_ShouldReturnFalse_IfToolsRequiredAtState0_AndRoomIsAtState0_AndTownDoesNotHaveTools() {
        boolean result = TownContainerChecks.hasSupplies(
                () -> ImmutableSet.of(0), // A room exists at state 0
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, (TestItem item) -> true // Town has the needed ingredients for state 1
                ),
                ImmutableMap.of(
                        // 0: No ingredients required
                        1, 1
                ),
                ImmutableMap.of(
                        0, required.apply("tool")
                        // 1: No additional tools required
                ),
                (predicate) -> ImmutableList.of(
                        // No tools
                        new TestItem("ingredient", 1)
                ).stream().filter(predicate).toList(),
                (state) -> ImmutableList.of(),
                (i) -> false
        );
        Assertions.assertFalse(result);
    }
}