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
    private Statuses status = Statuses.IDLE;

    public int getCapacity() {
        return 6;
    }

    public void addItem(I item) {
        if (this.inventoryIsFull()) {
            throw new IllegalStateException("Inventory is full");
        }
        Item emptySlot = inventory.stream().filter(Item::isEmpty).findFirst().get();
        inventory.set(inventory.indexOf(emptySlot), item);
    }

    public interface EmptyFactory<I extends Item> {
        I makeEmptyItem();
    }

    enum Signals {
        UNDEFINED,
        MORNING,
        NOON,
        EVENING,
        NIGHT
    }

    enum Statuses {
        IDLE,
        NO_SPACE,
        NO_FOOD,
        STAYING,
        GATHERING,
        RETURNED_SUCCESS,
        RETURNED_FAILURE,
        CAPTURED
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
        switch (sigs.getSignal()) {
            case MORNING -> {
                if (this.inventoryIsFull()) {
                    this.status = Statuses.NO_SPACE;
                    return;
                }
                if (this.inventoryHasFood()) {
                    this.status = Statuses.GATHERING;
                    return;
                }
                this.status = Statuses.NO_FOOD;
            }
            case NOON -> {
                if (!inventoryHasFood()) {
                    this.status = Statuses.STAYING;
                    return;
                }
                this.removeFood();
                this.addLoot(loot.getLoot());
            }
            case EVENING -> {
                // TODO: Random failure
                this.status = Statuses.RETURNED_SUCCESS;
            }
            case NIGHT -> {
                // TODO: Late return?
                // TODO: Gatherers can get captured and must be rescued by knight?
            }
        }
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

    private boolean inventoryIsFull() {
        return this.inventory.stream().noneMatch(Item::isEmpty);
    }

    public Statuses getStatus() {
        return status;
    }
}
