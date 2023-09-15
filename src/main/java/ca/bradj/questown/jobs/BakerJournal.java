package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO: This is almost entirely copy-pasted. Reduce duplication?
public class BakerJournal<I extends GathererJournal.Item<I>, H extends HeldItem<H, I>, ROOM> implements BakerStatuses.InventoryStateProvider {
    private final JournalItemList<H> inventory;
    private DefaultInventoryStateProvider<H> invState;
    private final int capacity;
    private final GathererJournal.SignalSource sigs;
    private GathererJournal.Status status;
    private List<GathererJournal.ItemsListener<H>> listeners = new ArrayList<>();
    private EmptyFactory<H> emptyFactory;
    private final ItemChecker<H> itemsToHold;

    @Override
    public boolean inventoryFull() {
        return invState.inventoryIsFull();
    }

    @Override
    public boolean hasNonSupplyItems() {
        return inventory.stream().filter(Predicates.not(GathererJournal.Item::isEmpty)).anyMatch(v -> !itemsToHold.shouldHoldForWork(status, v));
    }

    public interface ItemChecker<H> {
        boolean shouldHoldForWork(
                GathererJournal.Status status,
                H item
        );
    }

    public BakerJournal(
            GathererJournal.SignalSource sigs,
            int capacity,
            ItemChecker<H> itemsToHold,
            EmptyFactory<H> ef
    ) {
        this.sigs = sigs;
        this.itemsToHold = itemsToHold;
        this.inventory = new JournalItemList<>(capacity, ef);
        this.capacity = capacity;
        this.invState = new DefaultInventoryStateProvider<>(
                () -> ImmutableList.copyOf(this.inventory)
        );
        this.emptyFactory = ef;
    }

    public void initialize(BakerJournal.Snapshot<H> journal) {
        // TODO: Implement
    }

    public ImmutableList<Boolean> getSlotLockStatuses() {
        return ImmutableList.copyOf(this.inventory.stream().map(HeldItem::isLocked).toList());
    }

    public void setItems(Iterable<H> mcTownItemStream) {
        inventory.setItems(mcTownItemStream);
        updateItemListeners();
    }

    private void updateItemListeners() {
        ImmutableList<H> copyForListeners = ImmutableList.copyOf(inventory);
        this.listeners.forEach(l -> l.itemsChanged(copyForListeners));
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

    public void tick(
            BakerStatuses.TownStateProvider<MCRoom> townState,
            BakerStatuses.EntityStateProvider<MCRoom> entityState
    ) {
        if (status == GathererJournal.Status.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }
        Signals sig = sigs.getSignal();
        @Nullable GathererJournal.Status newStatus = BakerStatuses.getNewStatusFromSignal(
                status, sig, this, townState, entityState
        );
        if (newStatus != null) {
            changeStatus(newStatus);
        }
    }

    public GathererJournal.Status getStatus() {
        return status;
    }

    public void addItem(H item) {
        if (this.invState.inventoryIsFull()) {
            throw new IllegalStateException("Inventory is full");
        }
        H emptySlot = inventory.stream().filter(GathererJournal.Item::isEmpty).findFirst().get();
        inventory.set(inventory.indexOf(emptySlot), item);
        updateItemListeners();
        if (status == GathererJournal.Status.NO_FOOD && item.isFood()) { // TODO: Test
            changeStatus(GathererJournal.Status.IDLE);
        }
    }

    public boolean addItemIfSlotAvailable(H item) {
        if (Jobs.addItemIfSlotAvailable(this.inventory, this.invState, item)) {
            updateItemListeners();
            return true;
        }
        return false;
    }

    public void addItemListener(GathererJournal.ItemsListener<H> l) {
        this.listeners.add(l);
    }

    public void initializeStatus(GathererJournal.Status s) {
        this.status = s;
    }

    public boolean isInventoryFull() {
        return this.invState.inventoryIsFull();
    }

    public boolean isInventoryEmpty() {
        return invState.inventoryIsEmpty();
    }

    public boolean removeItem(H mct) {
        int index = inventory.lastIndexOf(mct);
        if (index < 0) {
            // Item must have already been removed by a different thread.
            return false;
        }

        inventory.set(index, emptyFactory.makeEmptyItem());
        updateItemListeners();
        // TODO: Add FARMER_EATING status
//        if (status == GathererJournal.Status.GATHERING_EATING) {
//            changeStatus(GathererJournal.Status.GATHERING);
//        }
        // TODO: Remove this, but make VisitorMobEntity into an itemlistener instead
        changeStatus(GathererJournal.Status.IDLE);
        return true;
    }

    public boolean hasAnyLootToDrop() {
        return inventory.stream().anyMatch(v -> !v.isEmpty() && !v.isLocked() && !itemsToHold.shouldHoldForWork(status, v));
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