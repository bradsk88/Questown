package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

class JobsCleanTest {

    ImmutableList<BiPredicate<Integer, TestItem>> bakerRecipe = ImmutableList.of(
            (s, item) -> "wheat".equals(item.value),
            (s, item) -> "wheat".equals(item.value),
            (s, item) -> "coal".equals(item.value)
    );

    @Test
    void shouldTakeItem_ifInventoryEmpty_AndItemIsValidForRecipe() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "", "", "", "", "", ""
                ).stream().map(TestItem::new).toList(),
                new TestItem("wheat")
        );
        Assertions.assertTrue(result);
    }

    @Test
    void shouldNotTakeItem_ifInventoryEmpty_AndItemIsNonRecipeItem() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "", "", "", "", "", ""
                ).stream().map(TestItem::new).toList(),
                new TestItem("bomb")
        );
        Assertions.assertFalse(result);
    }

    @Test
    void shouldNotTakeItem_ifInventoryFull() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "wheat", "wheat", "coal", "wheat", "wheat", "coal"
                ).stream().map(TestItem::new).toList(),
                new TestItem("wheat")
        );
        Assertions.assertFalse(result);
    }

    @Test
    void shouldNotTakeItem_ifInventoryHasOneOpening_AndItemIsNotPerfectFIt() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "wheat", "wheat", "coal", "wheat", "wheat", "" // <-- want coal here
                ).stream().map(TestItem::new).toList(),
                new TestItem("wheat")
        );
        Assertions.assertFalse(result);
    }

    @Test
    void getSupplyItemStatuses_ShouldReturnFalse_IfHasIngredientsForState1_ButMissingToolsFromState0() {
        @NotNull ImmutableMap<Integer, Boolean> result = JobsClean.getSupplyItemStatuses(
                () -> ImmutableList.of("wheat"),
                ImmutableMap.of(
                        // No ingredients are requited at state 0
                        1, (i) -> i.equals("wheat") // wheat is required at 1
                ),
                ImmutableMap.of(
                        0, (i) -> i.equals("tool") // tool is required at 0
                ),
                2
        );
        Assertions.assertFalse(result.get(0));
        Assertions.assertFalse(result.get(1));
    }

    @Test
    void getSupplyItemStatuses_ShouldReturnTrue_IfHasIngredientsForState1_AndToolFromState0() {
        @NotNull ImmutableMap<Integer, Boolean> result = JobsClean.getSupplyItemStatuses(
                () -> ImmutableList.of("wheat", "tool"),
                ImmutableMap.of(
                        // No ingredients are requited at state 0
                        1, (i) -> i.equals("wheat") // wheat is required at 1
                ),
                ImmutableMap.of(
                        0, (i) -> i.equals("tool") // tool is required at 0
                ),
                2
        );
        Assertions.assertTrue(result.get(0));
        Assertions.assertTrue(result.get(1));
    }

    @Test
    void getSupplyItemStatuses_ShouldReturnTrue_IfHasIngredientsForState1_AndNoToolsRequired() {
        @NotNull ImmutableMap<Integer, Boolean> result = JobsClean.getSupplyItemStatuses(
                () -> ImmutableList.of("wheat", "tool"),
                ImmutableMap.of(
                        // No ingredients are requited at state 0
                        1, (i) -> i.equals("wheat") // wheat is required at 1
                ),
                ImmutableMap.of(
                        // No tools
                ),
                2
        );
        Assertions.assertTrue(result.get(0));
        Assertions.assertTrue(result.get(1));
    }

    @Test
    void getSupplyItemStatuses_ShouldReturnTrueAtAllStatesAfterTool_IfOnlyToolsRequired() {
        @NotNull ImmutableMap<Integer, Boolean> result = JobsClean.getSupplyItemStatuses(
                () -> ImmutableList.of("wheat", "tool"),
                ImmutableMap.of(
                        // No ingredients required
                ),
                ImmutableMap.of(
                        1, i -> i.equals("tool")
                ),
                4
        );
        Assertions.assertTrue(result.get(0));
        Assertions.assertTrue(result.get(1));
        Assertions.assertTrue(result.get(2));
        Assertions.assertTrue(result.get(3));
        Assertions.assertTrue(result.get(4));
    }

    @Test
    void getSupplyItemStatuses_ShouldHandle_ToolsRequiredAtState0_NothingRequiredAtState1_SpecialRoomsAtState1() {
        @NotNull ImmutableMap<Integer, Boolean> result = JobsClean.getSupplyItemStatuses(
                () -> ImmutableList.of("tool"),
                ImmutableMap.of(
                        // No ingredients required
                ),
                ImmutableMap.of(
                        0, i -> i.equals("tool")
                ),
                4,
                false,
                ImmutableMap.of(
                        1, v -> true
                )
        );
        Assertions.assertTrue(result.get(0));
        Assertions.assertTrue(result.get(1));
        Assertions.assertTrue(result.get(2));
        Assertions.assertTrue(result.get(3));
        Assertions.assertTrue(result.get(4));
    }
}