package ca.bradj.questown.jobs;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;

public class DefaultInventoryStateProvider<I extends HeldItem<I, ?>> implements InventoryStateProvider<I> {

    private final CurrentItemsSource<I> itemsSource;

    public DefaultInventoryStateProvider(
            CurrentItemsSource<I> itemsSource
    ) {
        this.itemsSource = itemsSource;
    }

    public interface CurrentItemsSource<I extends HeldItem<I, ?>> {
        ImmutableCollection<I> GetCurrentItems();
    }

    @Override
    public boolean hasAnyDroppableLoot() {
        if (!this.isValid()) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().anyMatch(Predicates.and(
                Predicates.not(GathererJournal.Item::isFood),
                Predicates.not(GathererJournal.Item::isEmpty),
                Predicates.not(HeldItem::isLocked)
        ));
    }


    @Override
    public boolean inventoryIsFull() {
        if (!this.isValid()) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().noneMatch(GathererJournal.Item::isEmpty);
    }


    @Override
    public boolean inventoryHasFood() {
        if (!this.isValid()) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().anyMatch(GathererJournal.Item::isFood);
    }


    @Override
    public boolean hasAnyItems() {
        if (!this.isValid()) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().anyMatch(Predicates.not(GathererJournal.Item::isEmpty));
    }

    @Override
    public boolean isValid() {
        return itemsSource.GetCurrentItems().size() == 6;
    }
}
