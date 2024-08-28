package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Disabled;

import java.util.Objects;

@Disabled("Just keeping these around as reference")
public class GathererJournalTest {

    public static class TestItem implements Item<TestItem>, HeldItem<TestItem, TestItem> {
        public final String value;

        public TestItem(String value) {
            this.value = value;
        }

        @Override
        public boolean isEmpty() {
            return "".equals(value);
        }

        @Override
        public boolean isFood() {
            return "bread".equals(value);
        }

        @Override
        public TestItem shrink() {
            return new TestItem("");
        }

        @Override
        public String getShortName() {
            return value;
        }

        @Override
        public TestItem unit() {
            return this;
        }

        @Override
        public int quantity() {
            return 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestItem testItem = (TestItem) o;
            return Objects.equals(value, testItem.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "TestItem{" + "value='" + value + '\'' + '}';
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
            throw new IllegalCallerException("Locking is not expected here");
        }

        @Override
        public TestItem unlocked() {
            throw new IllegalCallerException("Locking is not expected here");
        }

        @Override
        public @Nullable String acquiredViaLootTablePrefix() {
            return null;
        }

        @Override
        public @Nullable String foundInBiome() {
            return null;
        }

        @Override
        public String toShortString() {
            return value;
        }
    }

}
