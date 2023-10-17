package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ca.bradj.questown.jobs.GathererJournalTest.TestItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class JobsCleanTest {

    ImmutableList<JobsClean.TestFn<TestItem>> bakerRecipe = ImmutableList.of(
            item -> "wheat".equals(item.value),
            item -> "wheat".equals(item.value),
            item -> "coal".equals(item.value)
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
    void shouldTakeItem_ifInventoryHasOneOpening_AndItemSatisfiesRecipe() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "wheat", "wheat", "coal", "wheat", "wheat", "" // <-- want coal here
                ).stream().map(TestItem::new).toList(),
                new TestItem("coal")
        );
        Assertions.assertTrue(result);
    }

    @Test
    void shouldTakeItem_ifInventoryHasOneOpening_AndItemSatisfiesRecipe_Reversed() {
        List<TestItem> inventory = new ArrayList<>(Lists.newArrayList(
                "wheat", "wheat", "coal", "wheat", "wheat", "" // <-- want coal here
        ).stream().map(TestItem::new).toList());
        Collections.reverse(inventory);
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                inventory,
                new TestItem("coal")
        );
        Assertions.assertTrue(result);
    }

    @Test
    void shouldTakeItem_ifInventoryHasOneOpening_AndItemSatisfiesRecipe_AllWheat() {
        List<TestItem> inventory = new ArrayList<>(Lists.newArrayList(
                "wheat", "wheat", "wheat", "wheat", "wheat", "" // <-- want coal here
        ).stream().map(TestItem::new).toList());
        Collections.reverse(inventory);
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                inventory,
                new TestItem("coal")
        );
        Assertions.assertTrue(result);
    }

    @Test
    void shouldTakeItem_ifInventoryHasTwoOpenings_AndItemSatisfiesRecipe_AllCoal() {
        List<TestItem> inventory = new ArrayList<>(Lists.newArrayList(
                "coal", "coal", "coal", "coal", "", "" // <-- want wheat here
        ).stream().map(TestItem::new).toList());
        Collections.reverse(inventory);
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                inventory,
                new TestItem("wheat")
        );
        Assertions.assertTrue(result);
    }

    @Test
    void shouldTakeItem_ifInventoryHasOneOpening_AndItemSatisfiesRecipe_AllCoal() {
        List<TestItem> inventory = new ArrayList<>(Lists.newArrayList(
                "coal", "coal", "coal", "coal", "coal", "" // <-- want wheat here
        ).stream().map(TestItem::new).toList());
        Collections.reverse(inventory);
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                inventory,
                new TestItem("wheat")
        );
        Assertions.assertTrue(result);
    }
}