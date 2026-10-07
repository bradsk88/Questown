package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks in the supply-acceptance contract behind the self-sufficiency soft-lock:
 *
 * <ul>
 *   <li>{@code shouldTakeItem} rejects when the needed-items set is {@code empty}
 *       (the branch the stalled leavers hit - verified on-disk 2026-10-07, worlds
 *       98/99: 100% "REJECT needed-empty").
 *   <li>The pass branch - needed set contains the food predicate, chest has the
 *       food, leaver's held slots empty - accepts.
 *   <li>A held item satisfying the needed predicate stops further carrying
 *       (removeItemsAlreadyHeld empties the needed set).
 * </ul>
 *
 * Pure - no MC classes (Item/HeldItem are the abstractions).
 */
class JobsCleanShouldTakeItemTest {

    static final class TestItem implements HeldItem<TestItem, TestItem> {
        static TestItem of(boolean empty, String name) {
            return new TestItem(empty, name);
        }

        private final boolean empty;
        private final String name;

        private TestItem(boolean empty, String name) {
            this.empty = empty;
            this.name = name;
        }

        @Override
        public boolean isEmpty() {
            return empty;
        }

        @Override
        public boolean isFood() {
            return !empty;
        }

        @Override
        public TestItem shrink() {
            return this;
        }

        @Override
        public String getShortName() {
            return name;
        }

        @Override
        public TestItem unit() {
            return this;
        }

        @Override
        public int quantity() {
            return empty ? 0 : 1;
        }

        @Override
        public boolean isLocked() {
            return false;
        }

        @Override
        public TestItem get() {
            return this;
        }

        @Override
        public TestItem locked() {
            return this;
        }

        @Override
        public TestItem unlocked() {
            return this;
        }

        @Override
        @Nullable
        public String acquiredViaLootTablePrefix() {
            return null;
        }

        @Override
        @Nullable
        public String foundInBiome() {
            return null;
        }

        @Override
        public String toShortString() {
            return name;
        }
    }

    private static final TestItem AIR = TestItem.of(true, "air");
    private static final TestItem MEAT = TestItem.of(false, "cooked_meat");
    private static final Predicate<TestItem> FOOD = i -> i.getShortName().equals("cooked_meat");

    @Test
    void neededEmpty_rejectsEvenMatchingFood() {
        // The stall branch (worlds 98/99): an empty needed-items set rejects
        // everything, including the item the job actually wants.
        assertFalse(JobsClean.shouldTakeItem(5, Collections.emptyList(),
                List.of(AIR, AIR),
                MEAT));
    }

    @Test
    void heldFull_rejectsAnyFood() {
        // Leaver already holding a non-empty item cannot take more.
        assertFalse(JobsClean.shouldTakeItem(2,
                List.of(FOOD),
                List.of(MEAT),
                MEAT));
    }

    @Test
    void neededMatch_emptyHeld_acceptsFood() {
        // The pass branch: needed set has the food predicate, chest item matches,
        // held slots all empty.
        assertTrue(JobsClean.shouldTakeItem(5,
                List.of(FOOD),
                List.of(AIR, AIR),
                MEAT));
    }

    @Test
    void neededAlreadyHeld_stopsFurtherCarrying() {
        // removeItemsAlreadyHeld: once the leaver holds the needed item, the
        // needed set empties and further taking is rejected (no double-carry).
        Collection<? extends Predicate<TestItem>> needed = List.of(FOOD);
        assertFalse(JobsClean.shouldTakeItem(5, needed,
                List.of(MEAT, AIR),
                MEAT));
    }
}
