package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

import java.util.Collection;

public interface Journal<STATUS, I> {
    void addItemListener(JournalItemsListener<I> l);

    STATUS getStatus();

    int getCapacity();

    void addStatusListener(StatusListener<STATUS> o);

    void initializeStatus(STATUS s);

    boolean hasAnyLootToDrop();

    ImmutableList<I> getItems();

    boolean removeItem(I mct);

    void addItem(I mcHeldItem);

    boolean isInventoryFull();

    ImmutableList<Boolean> getSlotLockStatuses();

    void setItems(Iterable<I> mcTownItemStream);

    boolean addItemIfSlotAvailable(I mcHeldItem);

    void setItemsNoUpdateNoCheck(ImmutableList<I> build);
}
