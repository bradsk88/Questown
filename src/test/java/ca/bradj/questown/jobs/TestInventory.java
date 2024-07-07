package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.InventoryHandle;
import ca.bradj.questown.jobs.declarative.ItemCountMismatch;
import ca.bradj.questown.jobs.declarative.ValidatedInventoryHandle;

import java.util.ArrayList;
import java.util.Collection;

public class TestInventory {
    public static ValidatedInventoryHandle<GathererJournalTest.TestItem> sized(int size) {
        ArrayList<GathererJournalTest.TestItem> villagerInventory = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            villagerInventory.add(new GathererJournalTest.TestItem(""));
        }

        InventoryHandle<GathererJournalTest.TestItem> inv = new InventoryHandle<>() {
            @Override
            public Collection<GathererJournalTest.TestItem> getItems() {
                return villagerInventory;
            }

            @Override
            public void set(
                    int ii,
                    GathererJournalTest.TestItem shrink
            ) {
                villagerInventory.set(ii, shrink);
            }
        };
        try {
            return new ValidatedInventoryHandle<>(inv, size);
        } catch (ItemCountMismatch e) {
            throw new RuntimeException(e);
        }
    }
}
