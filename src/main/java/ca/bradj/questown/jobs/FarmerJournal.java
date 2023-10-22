package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// TODO: This is almost entirely copy-pasted. Reduce duplication?
public class FarmerJournal<I extends GathererJournal.Item<I>, H extends HeldItem<H, I>> {
    private final JournalItemList<H> inventory;
    private DefaultInventoryStateProvider<H> invState;
    private final int capacity;
    private final SignalSource sigs;
    private GathererJournal.Status status;
    private List<JournalItemsListener<H>> listeners = new ArrayList<>();
    private EmptyFactory<H> emptyFactory;
    private final ItemChecker<H> itemsToHold;
    private ArrayList<StatusListener> statusListeners = new ArrayList<>();

    public void addStatusListener(StatusListener o) {
        this.statusListeners.add(o);
    }

    public interface ItemChecker<H> {
        boolean shouldHoldForWork(
                GathererJournal.Status status,
                H item
        );
    }

    public FarmerJournal(
            SignalSource sigs,
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

    public void initialize(FarmerJournal.Snapshot<H> journal) {
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
        changeStatus(GathererJournal.Status.IDLE);
    }

    protected void changeStatus(GathererJournal.Status s) {
        this.status = s;
        this.statusListeners.forEach(l -> l.statusChanged(this.status));
    }

    public Snapshot<H> getSnapshot(EmptyFactory<H> air) {
        return new Snapshot<>(status, ImmutableList.copyOf(inventory));
    }

    public int getCapacity() {
        return capacity;
    }

    public void tick(
            TownProvider town,
            FarmerStatuses.FarmStateProvider farm,
            EntityInvStateProvider entity,
            boolean isInFarm
    ) {
        if (status == GathererJournal.Status.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }
        Signals sig = sigs.getSignal();
        @Nullable GathererJournal.Status newStatus = FarmerStatuses.getNewStatusFromSignal(
                status, town, farm, entity, sig, isInFarm
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

    public void addItemListener(JournalItemsListener<H> l) {
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

        @Override
        public String jobStringValue() {
            return "farmer";
        }

        @Override
        public String toString() {
            return "FarmerJournal.Snapshot{" +
                    "status=" + status +
                    ", items=" + items +
                    '}';
        }
    }
}
