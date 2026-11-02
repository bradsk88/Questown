package ca.bradj.questown.jobs;
import ca.bradj.questown.jobs.declarative.SupplyItems;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code shouldTakeItem}: rejects when nothing is needed; accepts a needed item with empty hands; stops once the needed item is held. */
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
        // Nothing needed: take nothing.
        assertFalse(SupplyItems.shouldTakeItem(5, Collections.emptyList(),
                List.of(AIR, AIR),
                MEAT));
    }

    @Test
    void heldFull_rejectsAnyFood() {
        assertFalse(SupplyItems.shouldTakeItem(2,
                List.of(FOOD),
                List.of(MEAT),
                MEAT));
    }

    @Test
    void neededMatch_emptyHeld_acceptsFood() {
        assertTrue(SupplyItems.shouldTakeItem(5,
                List.of(FOOD),
                List.of(AIR, AIR),
                MEAT));
    }

    @Test
    void neededAlreadyHeld_stopsFurtherCarrying() {
        // Once the needed item is held, nothing more is needed.
        Collection<? extends Predicate<TestItem>> needed = List.of(FOOD);
        assertFalse(SupplyItems.shouldTakeItem(5, needed,
                List.of(MEAT, AIR),
                MEAT));
    }
}
