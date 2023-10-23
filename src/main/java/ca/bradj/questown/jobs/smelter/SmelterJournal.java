package ca.bradj.questown.jobs.smelter;

import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO: This is almost entirely copy-pasted. Reduce duplication?
public class SmelterJournal<
        I extends Item<I>,
        H extends HeldItem<H, I>
> implements Journal<SmelterStatus, H, SimpleSnapshot<SmelterStatus, H>> {
    public static final String NAME = "smelter";
    private final JournalItemList<H> inventory;
    private final DefaultInventoryStateProvider<H> invState;
    private final int capacity;
    private final SignalSource sigs;
    private SmelterStatus status;
    private final List<JournalItemsListener<H>> listeners = new ArrayList<>();
    private final EmptyFactory<H> emptyFactory;
    private final ArrayList<StatusListener<SmelterStatus>> statusListeners = new ArrayList<>();

    public void addStatusListener(StatusListener<SmelterStatus> o) {
        this.statusListeners.add(o);
    }

    public SmelterJournal(
            SignalSource sigs,
            int capacity,
            EmptyFactory<H> ef
    ) {
        this.sigs = sigs;
        this.inventory = new JournalItemList<>(capacity, ef);
        this.capacity = capacity;
        this.invState = new DefaultInventoryStateProvider<>(
                () -> ImmutableList.copyOf(this.inventory)
        );
        this.emptyFactory = ef;
    }

    public void initialize(SimpleSnapshot<SmelterStatus, H> journal) {
        this.setItems(journal.items());
        this.initializeStatus(journal.status());
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
        changeStatus(SmelterStatus.IDLE);
    }

    protected void changeStatus(SmelterStatus s) {
        this.status = s;
        this.statusListeners.forEach(l -> l.statusChanged(this.status));
    }

    public SimpleSnapshot<SmelterStatus, H> getSnapshot() {
        return new SimpleSnapshot<>(NAME, status, ImmutableList.copyOf(inventory));
    }

    public int getCapacity() {
        return capacity;
    }

    public void tick(
            JobTownProvider<SmelterStatus, MCRoom> townState,
            EntityLocStateProvider<MCRoom> entityState,
            EntityInvStateProvider<SmelterStatus> inventory
    ) {
        if (status.isUnset()) {
            throw new IllegalStateException("Must initialize status");
        }
        Signals sig = sigs.getSignal();
        @Nullable SmelterStatus newStatus = SmelterStatuses.getNewStatusFromSignal(
                status, sig, inventory, townState, entityState
        );
        if (newStatus != null) {
            changeStatus(newStatus);
        }
    }

    public SmelterStatus getStatus() {
        return status;
    }

    public void addItem(H item) {
        if (this.invState.inventoryIsFull()) {
            throw new IllegalStateException("Inventory is full");
        }
        H emptySlot = inventory.stream().filter(Item::isEmpty).findFirst().get();
        inventory.set(inventory.indexOf(emptySlot), item);
        updateItemListeners();
    }

    public boolean addItemIfSlotAvailable(H item) {
        if (Jobs.addItemIfSlotAvailable(this.inventory, this.invState, item)) {
            updateItemListeners();
            return true;
        }
        return false;
    }

    public void addItemListener(JournalItemsListener<H> l) {
        this.listeners.add(l);
    }

    public void initializeStatus(SmelterStatus s) {
        this.status = s;
    }

    public boolean isInventoryFull() {
        return this.invState.inventoryIsFull();
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
//        if (status == SmelterStatus.GATHERING_EATING) {
//            changeStatus(SmelterStatus.GATHERING);
//        }
        // TODO: Remove this, but make VisitorMobEntity into an itemlistener instead
        changeStatus(SmelterStatus.IDLE);
        return true;
    }

    public boolean hasAnyLootToDrop() {
        return inventory.stream().anyMatch(v -> !v.isEmpty() && !v.isLocked());
    }
}
