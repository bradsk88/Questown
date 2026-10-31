package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.JobsCleanShouldTakeItemTest.TestItem;
import ca.bradj.questown.logic.IPredicateCollection;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * r16 #9: the loot drop emptied every unlocked item, so a BOP depositor coming home with trip loot
 * dropped its own Block of Progress into the chest (and a hunter its sword). The drop now takes
 * only the work output (items no state of the job uses); with no output in hand it still drops
 * everything, so the "drop loot when no work possible" branches keep returning unused supplies.
 */
class HeldWorkOutputTest {

    private static final TestItem BOP = TestItem.of(false, "block_of_progress");
    private static final TestItem MUTTON = TestItem.of(false, "raw_mutton");
    private static final TestItem LEATHER = TestItem.of(false, "leather");
    private static final TestItem EMPTY = TestItem.of(true, "air");

    /** The BOP depositor's shape: BOP is the tool at state 0 and the ingredient at state 1. */
    private static HeldWorkOutput<TestItem, TestItem> bopDepositor() {
        Map<Integer, IPredicateCollection<TestItem>> ingredients = Map.of(1, named("block_of_progress"));
        Map<Integer, IPredicateCollection<TestItem>> tools = Map.of(0, named("block_of_progress"));
        return HeldWorkOutput.ofStates(2, ingredients::get, tools::get);
    }

    private static IPredicateCollection<TestItem> named(String name) {
        return new IPredicateCollection<>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public boolean test(TestItem item) {
                return name.equals(item.getShortName());
            }
        };
    }

    @Test
    void drop_takesTheLoot_andKeepsTheJobsOwnTool() {
        List<TestItem> dropped = bopDepositor().itemsToDrop(ImmutableList.of(BOP, MUTTON, LEATHER, EMPTY));

        Assertions.assertEquals(ImmutableList.of(MUTTON, LEATHER), dropped);
    }

    @Test
    void drop_withNoOutputInHand_stillReturnsTheSupplies() {
        List<TestItem> dropped = bopDepositor().itemsToDrop(ImmutableList.of(BOP, EMPTY));

        Assertions.assertEquals(ImmutableList.of(BOP), dropped);
    }

    @Test
    void anyOutput_ignoresSuppliesAndEmptySlots() {
        Assertions.assertFalse(bopDepositor().anyOutput(ImmutableList.of(BOP, EMPTY)));
        Assertions.assertTrue(bopDepositor().anyOutput(ImmutableList.of(BOP, MUTTON)));
    }
}
