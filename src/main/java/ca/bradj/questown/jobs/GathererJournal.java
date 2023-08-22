package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.IntStream;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

public class GathererJournal<I extends GathererJournal.Item<I>, H extends HeldItem<H, I> & GathererJournal.Item<H>> {
    private final SignalSource sigs;
    private final EmptyFactory<H> emptyFactory;
    private final Statuses.TownStateProvider storageCheck;
    private final int capacity;
    private final Converter<I, H> converter;
    private List<H> inventory;
    private boolean ate = false;
    private List<ItemsListener<H>> listeners = new ArrayList<>();
    private List<StatusListener> statusListeners = new ArrayList<>();
    private Status status = Status.UNSET;
    private DefaultInventoryStateProvider<H> invState = new DefaultInventoryStateProvider<>(() -> ImmutableList.copyOf(
            this.inventory));

    public interface ToolsChecker<H extends HeldItem<H, ?>> {
        Tools computeTools(Iterable<H> items);
    }

    private ToolsChecker<H> tools;

    public void lockSlot(int slot) {
        this.inventory.set(slot, this.inventory.get(slot).locked());
    }

    public void unlockSlot(int slotIndex) {
        this.inventory.set(slotIndex, this.inventory.get(slotIndex).unlocked());
    }

    public List<Boolean> getSlotLockStatuses() {
        return this.inventory.stream().map(HeldItem::isLocked).toList();
    }

    public interface Converter<I extends Item<I>, H extends HeldItem<H, I>> {
        H convert(I item);
    }

    public GathererJournal(
            SignalSource sigs,
            EmptyFactory<H> ef,
            Converter<I, H> converter,
            Statuses.TownStateProvider cont,
            int inventoryCapacity,
            ToolsChecker<H> tools
    ) {
        super();
        this.sigs = sigs;
        this.emptyFactory = ef;
        this.converter = converter;
        this.storageCheck = cont;
        this.inventory = new ArrayList<>();
        this.capacity = inventoryCapacity;
        for (int i = 0; i < getCapacity(); i++) {
            this.inventory.add(ef.makeEmptyItem());
        }
        this.tools = tools;
        updateItemListeners();
    }

    public static boolean debuggerReleaseControl() {
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        return true;
    }

    @Override
    public String toString() {
        return "GathererJournal{" + "ate=" + ate + ", inventory=" + inventory + ", status=" + status + '}';
    }

    public ImmutableList<H> getItems() {
        return ImmutableList.copyOf(inventory);
    }

    public void initializeStatus(Status statuses) {
        this.status = statuses;
    }

    public boolean hasAnyFood() {
        return inventory.stream().anyMatch(Item::isFood);
    }

    public boolean hasAnyLootToDrop() {
        return inventory.stream().anyMatch(v -> !v.isEmpty() && !v.isFood() && !v.isLocked());
    }

    public boolean hasAnyItems() {
        return !inventory.stream().allMatch(Item::isEmpty);
    }

    public boolean removeItem(H mct) {
        int index = inventory.lastIndexOf(mct);
        if (index < 0) {
            // Item must have already been removed by a different thread.
            return false;
        }

        inventory.set(index, emptyFactory.makeEmptyItem());
        updateItemListeners();
        if (status == Status.GATHERING_EATING) {
            changeStatus(Status.GATHERING);
        }
        // TODO: Remove this, but make VisitorMobEntity into an itemlistener instead
        changeStatus(Status.IDLE);
        return true;
    }

    private void updateItemListeners() {
        ImmutableList<H> copyForListeners = ImmutableList.copyOf(inventory);
        this.listeners.forEach(l -> l.itemsChanged(copyForListeners));
    }

    public H removeItem(int index) {
        H item = inventory.get(index);
        inventory.set(index, emptyFactory.makeEmptyItem());
        updateItemListeners();
        changeStatus(Status.IDLE);
        return item;
    }

    public void setItem(
            int index,
            H mcTownItem
    ) {
        H curItem = inventory.get(index);
        if (!curItem.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "Cannot set to %s. Slot %d is not empty. [has: %s]",
                    mcTownItem,
                    index,
                    curItem
            ));
        }
        setItemNoUpdateNoCheck(index, mcTownItem);
        updateItemListeners();
    }

    public void setItemNoUpdateNoCheck(
            int index,
            H mcHeldItem
    ) {
        inventory.set(index, mcHeldItem);
        changeStatus(Status.IDLE);
    }

    public void addStatusListener(StatusListener l) {
        this.statusListeners.add(l);
    }

    public void addItemsListener(ItemsListener<H> l) {
        this.listeners.add(l);
    }

    public int getCapacity() {
        return capacity;
    }

    public void addItem(H item) {
        if (this.invState.inventoryIsFull()) {
            throw new IllegalStateException("Inventory is full");
        }
        H emptySlot = inventory.stream().filter(Item::isEmpty).findFirst().get();
        inventory.set(inventory.indexOf(emptySlot), item);
        updateItemListeners();
        if (status == Status.NO_FOOD && item.isFood()) { // TODO: Test
            changeStatus(Status.IDLE);
        }
    }

    public void tick(
            LootProvider<I> loot
    ) {
        if (status == Status.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }

        Signals sig = sigs.getSignal();
        @Nullable GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                status,
                sig,
                this.invState,
                storageCheck
        );
        if (newStatus == Status.GATHERING_EATING) {
            Snapshot<H> ss = getSnapshot(emptyFactory).withStatus(newStatus).eatFoodFromInventory(emptyFactory, sig);
            this.setItems(ss.items());
            newStatus = ss.status();
        }
        if (newStatus == Status.RETURNED_SUCCESS && status != Status.RETURNED_SUCCESS && status != Status.IDLE) {
            Tools tools1 = this.tools.computeTools(inventory);
            this.addLoot(loot.getLoot(tools1));
        }
        if (newStatus != null) {
            changeStatus(newStatus);
        }
    }

    protected void changeStatus(Status s) {
        this.status = s;
        this.statusListeners.forEach(l -> l.statusChanged(this.status));
    }

    private boolean removeFood() {
        Optional<H> foodSlot = inventory.stream().filter(Item::isFood).findFirst();
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
                inventory.set(i, converter.convert(iterator.next()));
            }
        }
        updateItemListeners();
    }

    public Status getStatus() {
        return status;
    }

    // TODO: Create read-only class
    public Snapshot<H> getSnapshot(EmptyFactory<H> ef) {
        return new Snapshot<>(status, ImmutableList.copyOf(inventory));
    }

    public void initialize(Snapshot<H> journal) {
        this.setItems(journal.items());
        this.initializeStatus(journal.status());
    }

    public void setItems(Iterable<H> mcTownItemStream) {
        ImmutableList.Builder<H> b = ImmutableList.builder();
        mcTownItemStream.forEach(b::add);
        ImmutableList<H> initItems = b.build();
        if (initItems.size() != this.getCapacity()) {
            throw new IllegalArgumentException(String.format(
                    "Argument to setItems is wrong length. Should be %s",
                    this.getCapacity()
            ));
        }
        inventory.clear();
        inventory.addAll(initItems);
        updateItemListeners();
    }

    public void setItemsNoUpdateNoCheck(ImmutableList<H> build) {
        inventory.clear();
        inventory.addAll(build);
        changeStatus(Status.IDLE);
    }

    public enum Signals {
        UNDEFINED, MORNING, NOON, EVENING, NIGHT;

        public static Signals fromGameTime(long gameTime) {
            long dayTime = gameTime % 24000;
            if (dayTime < 6000) {
                return GathererJournal.Signals.MORNING;
            } else if (dayTime < 11500) {
                return GathererJournal.Signals.NOON;
            } else if (dayTime < 22000) {
                return GathererJournal.Signals.EVENING;
            } else {
                return GathererJournal.Signals.NIGHT;
            }
        }
    }

    // TODO: Add state for LEAVING and add signal for "left town"
    //  This will allow us to detect that food was removed by a player while leaving town and switch back to "NO_FOOD"
    public enum Status {
        UNSET, IDLE, NO_SPACE, NO_FOOD, STAYING, GATHERING, GATHERING_HUNGRY, GATHERING_EATING, RETURNING, RETURNING_AT_NIGHT, // TODO: Rename to "in evening" for accuracy?
        RETURNED_SUCCESS, DROPPING_LOOT, RETURNED_FAILURE, CAPTURED, RELAXING, NO_GATE;

        public static Status from(String s) {
            return switch (s) {
                case "IDLE" -> IDLE;
                case "NO_SPACE" -> NO_SPACE;
                case "NO_FOOD" -> NO_FOOD;
                case "NO_GATE" -> NO_GATE;
                case "STAYING" -> STAYING;
                case "GATHERING" -> GATHERING;
                case "GATHERING_HUNGRY" -> GATHERING_HUNGRY;
                case "GATHERING_EATING" -> GATHERING_EATING;
                case "RETURNED_SUCCESS" -> RETURNED_SUCCESS;
                case "RETURNED_FAILURE" -> RETURNED_FAILURE;
                case "DROPPING_LOOT" -> DROPPING_LOOT;
                case "RETURNING" -> RETURNING;
                case "RETURNING_AT_NIGHT" -> RETURNING_AT_NIGHT;
                case "CAPTURED" -> CAPTURED;
                case "RELAXING" -> RELAXING;
                default -> UNSET;
            };
        }

        public boolean isReturning() {
            return ImmutableList.of(RETURNING, RETURNING_AT_NIGHT).contains(this);
        }

        public boolean isGathering() {
            return ImmutableList.of(GATHERING, GATHERING_HUNGRY, GATHERING_HUNGRY).contains(this);
        }

        public boolean isPreparing() {
            return ImmutableList.of(NO_GATE, NO_FOOD).contains(this);
        }

        public boolean isFinishingUp() {
            return ImmutableList.of(RETURNED_SUCCESS, DROPPING_LOOT).contains(this);
        }
    }

    public interface StatusListener {
        void statusChanged(Status newStatus);
    }

    public interface ItemsListener<I> {
        void itemsChanged(ImmutableList<I> items);
    }

    public interface Item<I extends Item<I>> {
        boolean isEmpty();

        boolean isFood();

        I shrink();
    }

    public interface SignalSource {
        Signals getSignal();
    }

    public record Tools(boolean hasAxe) {
        public Tools withAxe() {
            return new Tools(true);
        }
    }

    public interface LootProvider<I extends GathererJournal.Item> {
        Collection<I> getLoot(Tools tools);
    }

    public interface EmptyFactory<I extends Item> {
        I makeEmptyItem();
    }

    public record Snapshot<H extends HeldItem<H, ?> & Item<H>>(Status status, ImmutableList<H> items) {

        public Snapshot<H> eatFoodFromInventory(
                EmptyFactory<H> ef,
                Signals signal
        ) {
            Status nextStatus = null;
            if (status == Status.GATHERING_EATING) {
                nextStatus = Status.RETURNING;
                if (signal == Signals.EVENING || signal == Signals.NIGHT) {
                    nextStatus = Status.RETURNING_AT_NIGHT;
                }
            } else if (status == Status.RETURNED_SUCCESS) {
                nextStatus = status;
            } else {
                throw new IllegalStateException(String.format(
                        "Eating is only supported when the status is %s or %s [Was: %s]",
                        Status.GATHERING_EATING,
                        Status.RETURNED_SUCCESS,
                        status
                ));
            }

            ArrayList<H> itemsCopy = new ArrayList<>(items);

            OptionalInt lastIndex = IntStream.range(0, itemsCopy.size())
                    .filter(i -> itemsCopy.get(i).isFood())
                    .reduce((a, b) -> b);

            if (lastIndex.isEmpty()) {
                throw new IllegalStateException("Cannot eat food when none in inventory");
            }

            itemsCopy.set(lastIndex.getAsInt(), ef.makeEmptyItem());

            return new Snapshot<>(nextStatus, ImmutableList.copyOf(itemsCopy));
        }

        public Snapshot<H> withStatus(@Nullable GathererJournal.Status newStatus) {
            return new Snapshot<>(newStatus, items);
        }

        @Override
        public String toString() {
            String itemsStr = "[\n\t" + String.join("\n\t", items.stream().map(Object::toString).toList()) + "\n]";
            return "Snapshot{" + "\n\tstatus=" + status + ",\n\titems=" + itemsStr + "\n}";
        }
    }
}
