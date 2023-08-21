package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

public class MutableInventoryStateProvider<I extends GathererJournal.Item> implements InventoryStateProvider<I> {

    private ImmutableCollection<I> items = ImmutableList.of();
    private final DefaultInventoryStateProvider<I> delegate = new DefaultInventoryStateProvider<>(
            () -> this.items
    );

    public static <I extends GathererJournal.Item<I>> MutableInventoryStateProvider<I> withInitialItems(
            ImmutableCollection<I> initialItems
    ) {
        MutableInventoryStateProvider<I> out = new MutableInventoryStateProvider<>();
        out.items = initialItems;
        return out;
    }

    private MutableInventoryStateProvider() {
    }

    public void updateItems(ImmutableCollection<I> newItems) {
        this.items = newItems;
    }

    @Override
    public boolean hasAnyLoot() {
        return delegate.hasAnyLoot();
    }

    @Override
    public boolean inventoryIsFull() {
        return delegate.inventoryIsFull();
    }

    @Override
    public boolean inventoryHasFood() {
        return delegate.inventoryHasFood();
    }

    @Override
    public boolean hasAnyItems() {
        return delegate.hasAnyItems();
    }

    @Override
    public boolean isValid() {
        return delegate.isValid();
    }
}
