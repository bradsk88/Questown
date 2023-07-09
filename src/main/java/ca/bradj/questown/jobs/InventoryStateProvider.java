package ca.bradj.questown.jobs;

public interface InventoryStateProvider<I extends GathererJournal.Item> {
    boolean hasAnyLoot();

    boolean inventoryIsFull();

    boolean inventoryHasFood();

    boolean hasAnyItems();
}
