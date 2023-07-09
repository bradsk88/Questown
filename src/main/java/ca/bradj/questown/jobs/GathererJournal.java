package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

public class GathererJournal<I extends GathererJournal.Item> {
    private final SignalSource sigs;
    private final EmptyFactory<I> emptyFactory;
    private final ContainerCheck storageCheck;
    private List<I> inventory; // TODO: Change to generic container and add adapter for MC Container
    private boolean ate = false;
    private ItemsListener<I> listener;
    private List<StatusListener> statusListeners = new ArrayList<>();
    private Statuses status = Statuses.UNSET;

    public GathererJournal(
            SignalSource sigs,
            EmptyFactory<I> ef,
            ContainerCheck cont
    ) {
        super();
        this.sigs = sigs;
        this.emptyFactory = ef;
        this.storageCheck = cont;
        this.inventory = new ArrayList<>();
        for (int i = 0; i < getCapacity(); i++) {
            this.inventory.add(ef.makeEmptyItem());
        }
        updateItemListeners();
    }

    public static boolean debuggerReleaseControl() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        return true;
    }

    @Override
    public String toString() {
        return "GathererJournal{" +
                "ate=" + ate +
                ", inventory=" + inventory +
                ", status=" + status +
                '}';
    }

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
        changeStatus(Statuses.IDLE);
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
        changeStatus(Statuses.IDLE);
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
        changeStatus(Statuses.IDLE);
    }

    public void addStatusListener(StatusListener l) {
        this.statusListeners.add(l);
    }

    public void setItemsListener(ItemsListener<I> l) {
        // TODO: Support multiple?
        this.listener = l;
    }

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

    public void tick(
            LootProvider<I> loot
    ) {
        if (status == Statuses.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }
        switch (sigs.getSignal()) {
            // TODO: Extract static "get status from signal" function and use it in timeWarp
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
                    if (status != Statuses.NO_SPACE && !storageCheck.IsTownStorageAvailable()) {
                        changeStatus(Statuses.NO_SPACE);
                        return;
                    }
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
                                status == Statuses.RETURNED_FAILURE ||
                                status == Statuses.RELAXING
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
                if (status == Statuses.GATHERING) {
                    this.removeFood();
                    this.addLoot(loot.getLoot());
                    changeStatus(Statuses.RETURNING);
                }
                // TODO: What if the gatherer is out but doesn't have food (somehow)
            }
            case EVENING -> {
                if (status == Statuses.STAYING) {
                    return;
                }
                if (status == Statuses.NO_FOOD) {
                    changeStatus(Statuses.STAYING);
                    return;
                }

                if (!hasAnyItems()) {
                    if (status != Statuses.RELAXING) {
                        changeStatus(Statuses.RELAXING);
                    }
                    return;
                }

                if (!storageCheck.IsTownStorageAvailable()) {
                    if (status != Statuses.NO_SPACE) {
                        changeStatus(Statuses.NO_SPACE);
                    }
                    return;
                }

                if (
                        this.status == Statuses.STAYING ||
                                status == Statuses.RETURNED_FAILURE ||
                                status == Statuses.RETURNED_SUCCESS ||
                                status == Statuses.RELAXING
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
        this.statusListeners.forEach(l -> l.statusChanged(this.status));
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

    // TODO: Create read-only class
    public Snapshot<I> getSnapshot(EmptyFactory<I> ef) {
        return new Snapshot<>(status, ate, ImmutableList.copyOf(inventory));
    }

    public void initialize(Snapshot<I> journal) {
        this.ate = journal.ate();
        this.initializeItems(journal.items());
        this.initializeStatus(journal.status());
    }

    public void initializeItems(Iterable<I> mcTownItemStream) {
        ImmutableList.Builder<I> b = ImmutableList.builder();
        mcTownItemStream.forEach(b::add);
        inventory.clear();
        ImmutableList<I> initItems = b.build();
        inventory.addAll(initItems);
        listener.itemsChanged(initItems);
    }

    public void setItemsNoUpdateNoCheck(ImmutableList<I> build) {
        inventory.clear();
        inventory.addAll(build);
        changeStatus(Statuses.IDLE);
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
        RETURNING,
        RETURNED_SUCCESS,
        RETURNED_FAILURE,
        CAPTURED,
        RELAXING;

        public static Statuses from(String s) {
            return switch (s) {
                case "IDLE" -> IDLE;
                case "NO_SPACE" -> NO_SPACE;
                case "NO_FOOD" -> NO_FOOD;
                case "STAYING" -> STAYING;
                case "GATHERING" -> GATHERING;
                case "RETURNED_SUCCESS" -> RETURNED_SUCCESS;
                case "RETURNED_FAILURE" -> RETURNED_FAILURE;
                case "RETURNING" -> RETURNING;
                case "CAPTURED" -> CAPTURED;
                case "RELAXING" -> RELAXING;
                default -> UNSET;
            };
        }
    }

    public interface StatusListener {
        void statusChanged(GathererJournal.Statuses newStatus);
    }

    public interface ItemsListener<I> {
        void itemsChanged(ImmutableList<I> items);
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

    public interface EmptyFactory<I extends Item> {
        I makeEmptyItem();
    }

    public interface ContainerCheck {
        boolean IsTownStorageAvailable();
    }

    public record Snapshot<I extends Item>(Statuses status, boolean ate, ImmutableList<I> items) {
        public static final Snapshot<MCTownItem> EMPTY = new Snapshot<>(Statuses.IDLE, false, ImmutableList.of());
    }
}
