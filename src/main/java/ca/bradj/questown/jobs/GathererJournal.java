package ca.bradj.questown.jobs;

import ca.bradj.questown.town.TownInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

public class GathererJournal<Inventory extends TownInventory<?, I>, I extends GathererJournal.Item> {

    private final SignalSource sigs;
    private final EmptyFactory<I> emptyFactory;

    public Collection<I> getItems() {
        return inventory;
    }

    public void initializeStatus(Statuses statuses) {
        this.status = statuses;
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
    }

    public void tick(
            LootProvider<I> loot
    ) {
        if (status == Statuses.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }
        switch (sigs.getSignal()) {
            case MORNING -> {
                if (
                        status == Statuses.NO_FOOD ||
                                status == Statuses.NO_SPACE ||
                                status == Statuses.GATHERING
                ) {
                    return;
                }
                if (this.inventoryIsFull()) {
                    this.changeStatus(Statuses.NO_SPACE);
                    return;
                }
                if (this.inventoryHasFood()) {
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
        foodSlot.ifPresent(food -> {
            inventory.set(inventory.lastIndexOf(food), emptyFactory.makeEmptyItem());
        });
        return foodSlot.isPresent();
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
