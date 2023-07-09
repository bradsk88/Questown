package ca.bradj.questown.jobs;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;

import java.util.Collection;

public class DefaultInventoryStateProvider<I extends GathererJournal.Item> implements InventoryStateProvider<I> {

    private final CurrentItemsSource<I> itemsSource;

    public DefaultInventoryStateProvider(
            CurrentItemsSource<I> itemsSource
    ) {
        this.itemsSource = itemsSource;
    }

    public interface CurrentItemsSource<I extends GathererJournal.Item> {
        ImmutableCollection<I> GetCurrentItems();
    }

    @Override
    public boolean hasAnyLoot() {
        if (this.itemsSource.GetCurrentItems().size() != 6) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().anyMatch(Predicates.and(
                Predicates.not(GathererJournal.Item::isFood),
                Predicates.not(GathererJournal.Item::isEmpty)
        ));
    }


    @Override
    public boolean inventoryIsFull() {
        if (this.itemsSource.GetCurrentItems().size() != 6) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().noneMatch(GathererJournal.Item::isEmpty);
    }


    @Override
    public boolean inventoryHasFood() {
        if (this.itemsSource.GetCurrentItems().size() != 6) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().anyMatch(GathererJournal.Item::isFood);
    }


    @Override
    public boolean hasAnyItems() {
        if (this.itemsSource.GetCurrentItems().size() != 6) {
            throw new IllegalStateException("Inventory must be size 6");
        }
        return this.itemsSource.GetCurrentItems().stream().anyMatch(Predicates.not(GathererJournal.Item::isEmpty));
    }
}
