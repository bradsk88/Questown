package ca.bradj.questown.jobs;

public interface InventoryStateProvider<I extends Item> {
    boolean hasAnyDroppableLoot();

    boolean inventoryIsFull();

    boolean inventoryHasFood();

    boolean hasAnyItems();

    boolean isValid();
}
