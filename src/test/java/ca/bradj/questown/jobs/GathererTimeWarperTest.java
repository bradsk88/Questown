package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class GathererTimeWarperTest {
    private static final GathererTimeWarper.Town<GathererJournalTest.TestItem> infiniteStorageTown = new GathererTimeWarper.Town<>() {
        @Override
        public ImmutableList<GathererJournalTest.TestItem> depositItems(ImmutableList<GathererJournalTest.TestItem> itemsToDeposit) {
            return ImmutableList.of(
                    new GathererJournalTest.TestItem(""),
                    new GathererJournalTest.TestItem(""),
                    new GathererJournalTest.TestItem(""),
                    new GathererJournalTest.TestItem(""),
                    new GathererJournalTest.TestItem(""),
                    new GathererJournalTest.TestItem("")
            );
        }

        @Override
        public boolean IsStorageAvailable() {
            return true;
        }

        @Override
        public boolean hasGate() {
            return true;
        }
    };

    @Test
    void TestShouldDepositAllItems() {
        @NotNull List<GathererJournalTest.TestItem> finalInventory = GathererTimeWarper.<
                GathererJournalTest.TestItem, GathererJournalTest.TestItem
                >dropLoot(
                ImmutableList.of(
                        new GathererJournalTest.TestItem("flint"),
                        new GathererJournalTest.TestItem("wood"),
                        new GathererJournalTest.TestItem("stone"),
                        new GathererJournalTest.TestItem("iron"),
                        new GathererJournalTest.TestItem("gold"),
                        new GathererJournalTest.TestItem("diamond")
                ), infiniteStorageTown,
                t -> t, () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(ImmutableList.of(
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem("")
        ), finalInventory);
    }

    public static class LockableTestItem extends GathererJournalTest.TestItem {

        private boolean locked;

        public LockableTestItem(
                String value,
                boolean locked
        ) {
            super(value);
            this.locked = locked;
        }

        @Override
        public GathererJournalTest.TestItem locked() {
            return new LockableTestItem(value, true);
        }

        @Override
        public GathererJournalTest.TestItem unlocked() {
            return new LockableTestItem(value, false);
        }

        @Override
        public boolean isLocked() {
            return locked;
        }
    }

    @Test
    void TestShouldDepositOnlyUnlockedItems() {
        @NotNull List<GathererJournalTest.TestItem> finalInventory = GathererTimeWarper.<
                GathererJournalTest.TestItem, GathererJournalTest.TestItem
                >dropLoot(
                ImmutableList.of(
                        new LockableTestItem("flint", false),
                        new LockableTestItem("wood", true),
                        new LockableTestItem("stone", false),
                        new LockableTestItem("iron", false),
                        new LockableTestItem("gold", false),
                        new LockableTestItem("diamond", false)
                ), infiniteStorageTown,
                t -> t, () -> new LockableTestItem("", false)
        );
        Assertions.assertEquals(ImmutableList.of(
                new LockableTestItem("", false),
                new LockableTestItem("wood", true),
                new LockableTestItem("", false),
                new LockableTestItem("", false),
                new LockableTestItem("", false),
                new LockableTestItem("", false)
        ), finalInventory);
    }

}