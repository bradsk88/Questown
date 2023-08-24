package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

// TODO: This is almost entirely copy-pasted. Reduce duplication?
public class CookJournal<I extends GathererJournal.Item<I>, H extends HeldItem<H, I>> {
    private final List<H> inventory;
    private final int capacity;
    private GathererJournal.Status status;

    public CookJournal(
            int capacity
    ) {
        this.inventory = new ArrayList<>();
        this.capacity = capacity;
    }

    public void initialize(CookJournal.Snapshot<H> journal) {
        // TODO: Implement
    }

    public ImmutableList<Boolean> getSlotLockStatuses() {
        return ImmutableList.copyOf(this.inventory.stream().map(HeldItem::isLocked).toList());
    }

    public void setItems(Iterable<H> mcTownItemStream) {
        Jobs.setItemsOnJournal(mcTownItemStream, inventory, capacity);
        updateItemListeners();
    }

    private void updateItemListeners() {
        ImmutableList<H> copyForListeners = ImmutableList.copyOf(inventory);
        // TODO: Implement listeners
//        this.listeners.forEach(l -> l.itemsChanged(copyForListeners));
    }

    public ImmutableList<H> getItems() {
        return ImmutableList.copyOf(inventory);
    }

    public void setItemsNoUpdateNoCheck(ImmutableList<H> build) {
        inventory.clear();
        inventory.addAll(build);
        changeStatus(GathererJournal.Status.IDLE);
    }

    protected void changeStatus(GathererJournal.Status s) {
        this.status = s;
        // TODO: IMplement status listeners
//        this.statusListeners.forEach(l -> l.statusChanged(this.status));
    }

    public Snapshot<H> getSnapshot(EmptyFactory<H> air) {
        return new Snapshot<>(status, ImmutableList.copyOf(inventory));
    }

    public record Snapshot<H extends HeldItem<H, ?> & GathererJournal.Item<H>>(
            GathererJournal.Status status, ImmutableList<H> items
    ) implements ca.bradj.questown.jobs.Snapshot<H> {
        @Override
        public String statusStringValue() {
            return this.status().name();
        }
    }
}
