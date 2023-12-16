package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.ProductionStatuses;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// TODO: This is almost entirely copy-pasted. Reduce duplication?
public class ProductionJournal<
        I extends Item<I>,
        H extends HeldItem<H, I>
> implements Journal<ProductionStatus, H, SimpleSnapshot<ProductionStatus, H>> {
    private final JournalItemList<H> inventory;
    private final DefaultInventoryStateProvider<H> invState;
    private final JobID jobId;
    private final int capacity;
    private final SignalSource sigs;
    private final IStatusFactory<ProductionStatus> statusFactory;
    private @Nullable ProductionStatus status;
    private final List<JournalItemsListener<H>> listeners = new ArrayList<>();
    private final EmptyFactory<H> emptyFactory;
    private final ArrayList<StatusListener> statusListeners = new ArrayList<>();

    public Function<Void, Void> addStatusListener(StatusListener o) {
        this.statusListeners.add(o);
        return (x) -> {
            this.removeStatusListener(o);
            return null;
        };
    }

    @Override
    public void removeStatusListener(StatusListener o) {
        this.statusListeners.remove(o);
    }

    public ProductionJournal(
            @NotNull JobID jobId,
            SignalSource sigs,
            int capacity,
            EmptyFactory<H> ef,
            IStatusFactory<ProductionStatus> sf
    ) {
        this.jobId = jobId;
        this.sigs = sigs;
        this.inventory = new JournalItemList<>(capacity, ef);
        this.capacity = capacity;
        this.invState = new DefaultInventoryStateProvider<>(
                () -> ImmutableList.copyOf(this.inventory)
        );
        this.emptyFactory = ef;
        this.statusFactory = sf;
    }

    public void initialize(SimpleSnapshot<ProductionStatus, H> journal) {
        this.setItems(journal.items());
        this.initializeStatus(journal.status());
    }

    @Override
    public boolean isInitialized() {
        return status != null && !status.isUnset();
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
        changeStatus(statusFactory.idle());
    }

    protected void changeStatus(ProductionStatus s) {
        this.status = s;
        this.statusListeners.forEach(l -> l.statusChanged(this.status));
    }

    public SimpleSnapshot<ProductionStatus, H> getSnapshot() {
        return new SimpleSnapshot<>(jobId, status, ImmutableList.copyOf(inventory));
    }

    public int getCapacity() {
        return capacity;
    }

    public void tick(
            JobTownProvider<MCRoom> townState,
            EntityLocStateProvider<MCRoom> entityState,
            EntityInvStateProvider<Integer> inventory,
            IProductionStatusFactory<ProductionStatus> factory,
            boolean prioritizeExtraction
    ) {
        if (status == null || status.isUnset()) {
            throw new IllegalStateException("Must initialize status");
        }
        Signals sig = sigs.getSignal();
        @Nullable ProductionStatus newStatus = ProductionStatuses.getNewStatusFromSignal(
                status, sig, inventory, townState, entityState, factory, prioritizeExtraction
        );
        if (newStatus != null) {
            changeStatus(newStatus);
        }
    }

    public ProductionStatus getStatus() {
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

    public void initializeStatus(ProductionStatus s) {
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
        setItem(index, emptyFactory.makeEmptyItem());
        return true;
    }

    public boolean hasAnyLootToDrop() {
        return inventory.stream().anyMatch(v -> !v.isEmpty() && !v.isLocked());
    }

    public void setItem(
            int idx,
            H mcHeldItem
    ) {
        inventory.set(idx, mcHeldItem);
        updateItemListeners();
        // TODO: Remove this, but make VisitorMobEntity into an itemlistener instead
        changeStatus(ProductionStatus.FACTORY.idle());
    }

    public JobID getJobId() {
        return jobId;
    }
}
