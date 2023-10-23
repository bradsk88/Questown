package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public interface ItemsHolder<I> {
    void addItemListener(JournalItemsListener<I> l);

    int getCapacity();

    boolean hasAnyLootToDrop();

    ImmutableList<I> getItems();

    boolean removeItem(I mct);

    void addItem(I mcHeldItem);

    boolean isInventoryFull();

    void setItems(Iterable<I> mcTownItemStream);

    boolean addItemIfSlotAvailable(I mcHeldItem);

    void setItemsNoUpdateNoCheck(ImmutableList<I> build);
}
