package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
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

}