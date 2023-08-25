package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO: This is almost entirely copy-pasted. Reduce duplication?
public class FarmerJournal<I extends GathererJournal.Item<I>, H extends HeldItem<H, I>> {
    private final List<H> inventory;
    private final int capacity;
    private final GathererJournal.SignalSource sigs;
    private GathererJournal.Status status;

    public FarmerJournal(
            GathererJournal.SignalSource sigs,
            int capacity
    ) {
        this.sigs = sigs;
        this.inventory = new ArrayList<>();
        this.capacity = capacity;
    }

    public void initialize(FarmerJournal.Snapshot<H> journal) {
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
        // TODO: Implement status listeners
//        this.statusListeners.forEach(l -> l.statusChanged(this.status));
    }

    public Snapshot<H> getSnapshot(EmptyFactory<H> air) {
        return new Snapshot<>(status, ImmutableList.copyOf(inventory));
    }

    public int getCapacity() {
        return capacity;
    }

    public void tick(FarmerJob e) {
        if (status == GathererJournal.Status.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }
        Signals sig = sigs.getSignal();
        @Nullable GathererJournal.Status newStatus = FarmerStatuses.getNewStatusFromSignal(
                status, sig
        );
        if (newStatus != null) {
            changeStatus(newStatus);
        }
    }

    public GathererJournal.Status getStatus() {
        return status;
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
