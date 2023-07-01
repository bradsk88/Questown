package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.TownInventory;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

public class GathererJournal<Inventory extends TownInventory<?, I>, I extends GathererJournal.Item> {

    private final SignalSource sigs;
    private final EmptyFactory<I> emptyFactory;
    private boolean ate = false;
    private ItemsListener<I> listener;

    public ImmutableList<I> getItems() {
        return ImmutableList.copyOf(inventory);
    }

    public void initializeStatus(Statuses statuses) {
        this.status = statuses;
    }

    public boolean hasAnyFood() {
        return inventory.stream().anyMatch(Item::isFood);
    }

    public boolean hasAnyNonFood() {
        return inventory.stream().anyMatch(v -> !v.isEmpty() && !v.isFood());
    }

    public boolean hasAnyItems() {
        return !inventory.stream().allMatch(Item::isEmpty);
    }

    public boolean removeItem(I mct) {
        int index = inventory.lastIndexOf(mct);
        if (index < 0) {
            // Item must have already been removed by a different thread.
            return false;
        }

        inventory.set(index, emptyFactory.makeEmptyItem());
        updateItemListeners();
        if (!hasAnyNonFood()) { // TODO: Test
            changeStatus(Statuses.IDLE);
        }
        return true;
    }

    private void updateItemListeners() {
        if (this.listener == null) {
            return;
        }
        this.listener.itemsChanged(ImmutableList.copyOf(inventory));
    }

    public I removeItem(int index) {
        I item = inventory.get(index);
        inventory.set(index, emptyFactory.makeEmptyItem());
        updateItemListeners();
        if (!hasAnyNonFood()) { // TODO: Test
            changeStatus(Statuses.IDLE);
        }
        return item;
    }

    public void setItem(
            int index,
            I mcTownItem
    ) {
        I curItem = inventory.get(index);
        if (!curItem.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Cannot set to %s. Slot %d is not empty. [has: %s]", mcTownItem, index, curItem)
            );
        }
        setItemNoUpdateNoCheck(index, mcTownItem);
        updateItemListeners();
    }

    public void setItemNoUpdateNoCheck(
            int index,
            I mcTownItem
    ) {
        inventory.set(index, mcTownItem);
    }

    public interface ItemsListener<I> {
        void itemsChanged(ImmutableList<I> items);
    }

    public void setItemsListener(ItemsListener<I> l) {
        // TODO: Support multiple?
        this.listener = l;
    }

    public interface Item {
        boolean isEmpty();

        boolean isFood();
    }

    public interface SignalSource {
        Signals getSignal();
    }

    public interface LootProvider<I extends GathererJournal.Item> {
        Collection<I> getLoot();
    }

    private final ArrayList<I> inventory;
    private Statuses status = Statuses.UNSET;

    public int getCapacity() {
        return 6;
    }

    public void addItem(I item) {
        if (this.inventoryIsFull()) {
            throw new IllegalStateException("Inventory is full");
        }
        Item emptySlot = inventory.stream().filter(Item::isEmpty).findFirst().get();
        inventory.set(inventory.indexOf(emptySlot), item);
        updateItemListeners();
        if (status == Statuses.NO_FOOD && item.isFood()) { // TODO: Test
            changeStatus(Statuses.IDLE);
        }
    }

    public interface EmptyFactory<I extends Item> {
        I makeEmptyItem();
    }

    public enum Signals {
        UNDEFINED,
        MORNING,
        NOON,
        EVENING,
        NIGHT
    }

    // TODO: Add state for LEAVING and add signal for "left town"
    //  This will allow us to detect that food was removed by a player while leaving town and switch back to "NO_FOOD"
    public enum Statuses {
        UNSET,
        IDLE,
        NO_SPACE,
        NO_FOOD,
        STAYING,
        GATHERING,
        RETURNED_SUCCESS,
        RETURNED_FAILURE,
        RETURNING,
        CAPTURED;
    }

    public GathererJournal(
            SignalSource sigs,
            EmptyFactory<I> ef
    ) {
        super();
        this.sigs = sigs;
        this.emptyFactory = ef;
        this.inventory = new ArrayList<>();
        for (int i = 0; i < getCapacity(); i++) {
            this.inventory.add(ef.makeEmptyItem());
        }
        updateItemListeners();
    }

    public void tick(
            LootProvider<I> loot
    ) {
        if (status == Statuses.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }
        switch (sigs.getSignal()) {
            case MORNING -> {
                this.ate = false;
                if (
                        status == Statuses.NO_FOOD ||
                                status == Statuses.NO_SPACE ||
                                status == Statuses.GATHERING
                ) {
                    return;
                }

                if (this.hasAnyNonFood()) {
                    if (status == Statuses.RETURNED_SUCCESS) {
                        return;
                    }
                    this.changeStatus(Statuses.RETURNED_SUCCESS);
                    return;
                }


                if (this.inventoryIsFull()) {
                    this.changeStatus(Statuses.NO_SPACE);
                    return;
                }
                if (this.inventoryHasFood()) {
                    Questown.LOGGER.debug("Inventory has: {}", this.getItems());
                    this.changeStatus(Statuses.GATHERING);
                    return;
                }
                this.changeStatus(Statuses.NO_FOOD);
            }
            case NOON -> {
                if (
                        this.status == Statuses.STAYING ||
                                status == Statuses.RETURNING
                ) {
                    return;
                }
                if (
                        status == Statuses.RETURNED_SUCCESS ||
                                status == Statuses.RETURNED_FAILURE
                ) {
                    changeStatus(Statuses.NO_FOOD);
                    return;
                }
                if (
                        this.status == Statuses.NO_FOOD ||
                                this.status == Statuses.NO_SPACE
                ) {
                    changeStatus(Statuses.STAYING);
                    return;
                }
                changeStatus(Statuses.RETURNING);
                // TODO: What if the gatherer is out but doesn't have food (somehow)
                this.removeFood();
                this.addLoot(loot.getLoot());
            }
            case EVENING -> {
                if (
                        status == Statuses.NO_FOOD ||
                                status == Statuses.NO_SPACE
                ) {
                    changeStatus(Statuses.STAYING);
                    return;
                }

                if (
                        this.status == Statuses.STAYING ||
                                status == Statuses.RETURNED_FAILURE ||
                                status == Statuses.RETURNED_SUCCESS
                ) {
                    return;
                }
                // TODO: Random failure
                if (!ate) {
                    this.removeFood();
                    this.addLoot(loot.getLoot());
                }
                this.changeStatus(Statuses.RETURNED_SUCCESS);
            }
            case NIGHT -> {
                if (
                        this.status == Statuses.STAYING ||
                                this.status == Statuses.RETURNED_FAILURE ||
                                this.status == Statuses.RETURNED_SUCCESS
                ) {
                    return;
                }
                // TODO: Late return?
                // TODO: Gatherers can get captured and must be rescued by knight?
            }
        }
    }

    protected void changeStatus(Statuses s) {
        this.status = s;
    }

    private boolean removeFood() {
        Optional<I> foodSlot = inventory.stream().filter(Item::isFood).findFirst();
        boolean hadFood = foodSlot.isPresent();
        foodSlot.ifPresentOrElse(food -> {
            inventory.set(inventory.lastIndexOf(food), emptyFactory.makeEmptyItem());
            updateItemListeners();
            Questown.LOGGER.debug("Gatherer ate: {}", food);
        }, () -> Questown.LOGGER.error("Gather was somehow out with no food!"));
        this.ate = hadFood;
        return hadFood;
    }

    private void addLoot(Collection<I> loot) {
        Iterator<I> iterator = loot.iterator();
        for (int i = 0; i < getCapacity(); i++) {
            if (!iterator.hasNext()) {
                break;
            }
            // TODO: On extended trips, replace non-food
            if (inventory.get(i).isEmpty()) {
                inventory.set(i, iterator.next());
            }
        }
        updateItemListeners();
    }

    private boolean inventoryHasFood() {
        return this.inventory.stream().anyMatch(Item::isFood);
    }

    public boolean inventoryIsFull() {
        return this.inventory.stream().noneMatch(Item::isEmpty);
    }

    public Statuses getStatus() {
        return status;
    }
}
