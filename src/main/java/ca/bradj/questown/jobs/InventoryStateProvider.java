package ca.bradj.questown.jobs;

public interface InventoryStateProvider<I extends GathererJournal.Item> {
    boolean hasAnyDroppableLoot();

    boolean inventoryIsFull();

    boolean inventoryHasFood();

    boolean hasAnyItems();

    boolean isValid();
}
