package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
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
    private final GathererStatuses.TownStateProvider storageCheck;
    private final Converter<I, H> converter;
    private JournalItemList<H> inventory;
    private boolean ate = false;
    private List<JournalItemsListener<H>> listeners = new ArrayList<>();
    private List<StatusListener> statusListeners = new ArrayList<>();
    private Status status = Status.UNSET;
    private DefaultInventoryStateProvider<H> invState = new DefaultInventoryStateProvider<>(() -> ImmutableList.copyOf(
            this.inventory));

    public static Status getStatusFromEntityData(String s) {
        if (s == null || s.isEmpty()) {
            return Status.UNSET;
        }
        return Status.from(s);
    }

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

    public ImmutableList<Boolean> getSlotLockStatuses() {
        return ImmutableList.copyOf(this.inventory.stream().map(HeldItem::isLocked).toList());
    }

    public interface Converter<I extends Item<I>, H extends HeldItem<H, I>> {
        H convert(I item);
    }

    public GathererJournal(
            SignalSource sigs,
            EmptyFactory<H> ef,
            Converter<I, H> converter,
            GathererStatuses.TownStateProvider cont,
            int inventoryCapacity,
            ToolsChecker<H> tools
    ) {
        super();
        this.sigs = sigs;
        this.emptyFactory = ef;
        this.converter = converter;
        this.storageCheck = cont;
        this.inventory = new JournalItemList<>(inventoryCapacity, ef);
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

    public void addItemsListener(JournalItemsListener<H> l) {
        this.listeners.add(l);
    }

    public int getCapacity() {
        return inventory.size();
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

    public boolean addItemIfSlotAvailable(H item) {
        if (Jobs.addItemIfSlotAvailable(this.inventory, this.invState, item)) {
            updateItemListeners();
            return true;
        }
        return false;
    }

    public void tick(
            LootProvider<I> loot
    ) {
        if (status == Status.UNSET) {
            throw new IllegalStateException("Must initialize status");
        }

        Signals sig = sigs.getSignal();
        @Nullable GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
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
        for (int i = 0; i < inventory.size(); i++) {
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
        inventory.setItems(mcTownItemStream);
        updateItemListeners();
    }

    public void setItemsNoUpdateNoCheck(ImmutableList<H> build) {
        inventory.clear();
        inventory.addAll(build);
        changeStatus(Status.IDLE);
    }

    // TODO: Add state for LEAVING and add signal for "left town"
    //  This will allow us to detect that food was removed by a player while leaving town and switch back to "NO_FOOD"
    public enum Status implements IStatus<Status> {
        UNSET, IDLE, NO_SPACE, NO_FOOD, STAYING, GATHERING, GATHERING_HUNGRY, GATHERING_EATING, RETURNING, RETURNING_AT_NIGHT, // TODO: Rename to "in evening" for accuracy?
        RETURNED_SUCCESS, DROPPING_LOOT, RETURNED_FAILURE, CAPTURED, RELAXING, NO_GATE,
        GOING_TO_JOBSITE,
        // TODO: Move to farmer-specific status
        FARMING_HARVESTING, FARMING_RANDOM_TEND, FARMING_TILLING, FARMING_PLANTING, FARMING_BONING, FARMING_COMPOSTING,
        // TODO: Move to baker-specific status
        COLLECTING_SUPPLIES, NO_SUPPLIES, BAKING, COLLECTING_BREAD, LEAVING_FARM, BAKING_FUELING, FARMING_WEEDING;

        public static final IStatusFactory<Status> FACTORY = new IStatusFactory<>() {
            @Override
            public Status droppingLoot() {
                return DROPPING_LOOT;
            }

            @Override
            public Status noSpace() {
                return NO_SPACE;
            }

            @Override
            public Status goingToJobSite() {
                return GOING_TO_JOBSITE;
            }

            @Override
            public Status noSupplies() {
                return NO_SUPPLIES;
            }

            @Override
            public Status collectingSupplies() {
                return COLLECTING_SUPPLIES;
            }

            @Override
            public Status idle() {
                return IDLE;
            }

            @Override
            public Status collectingFinishedProduct() {
                throw new UnsupportedOperationException("Gatherers do not generate products");
            }
        };

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
                case "FARMING_RANDOM_TEND" -> FARMING_RANDOM_TEND;
                case "FARMING_HARVESTING" -> FARMING_HARVESTING;
                case "FARMING_PLANTING" -> FARMING_PLANTING;
                case "FARMING_TILLING" -> FARMING_TILLING;
                case "FARMING_COMPOSTING" -> FARMING_COMPOSTING;
                case "FARMING_BONING" -> FARMING_BONING;
                case "FARMING_WEEDING" -> FARMING_WEEDING;
                case "GOING_TO_JOBSITE" -> GOING_TO_JOBSITE;
                case "LEAVING_FARM" -> LEAVING_FARM;
                case "COLLECTING_SUPPLIES" -> COLLECTING_SUPPLIES;
                case "NO_SUPPLIES" -> NO_SUPPLIES;
                case "BAKING" -> BAKING;
                case "BAKING_FUELING" -> BAKING_FUELING;
                case "COLLECTING_BREAD" -> COLLECTING_BREAD;
                // TODO: Can this be protected by a compiler check?
                default -> throw new IllegalArgumentException("Unexpected status " + s);
            };
        }

        public boolean isReturning() {
            return ImmutableList.of(RETURNING, RETURNING_AT_NIGHT).contains(this);
        }

        public boolean isWorking() {
            return ImmutableList.of(GATHERING, GATHERING_HUNGRY, GATHERING_HUNGRY).contains(this) || isFarmingWork();
        }

        public boolean isFarmingWork() {
            return ImmutableList.of(
                    FARMING_HARVESTING,
                    FARMING_BONING,
                    FARMING_PLANTING,
                    FARMING_TILLING,
                    FARMING_COMPOSTING,
                    FARMING_WEEDING
            ).contains(this);
        }

        public boolean isBakingWork() {
            return ImmutableList.of(
                    BAKING,
                    BAKING_FUELING,
                    COLLECTING_BREAD
            ).contains(this);
        }

        public boolean isPreparing() {
            return ImmutableList.of(NO_GATE, NO_FOOD, GOING_TO_JOBSITE).contains(this);
        }

        public boolean isFinishingUp() {
            return ImmutableList.of(RETURNED_SUCCESS, DROPPING_LOOT).contains(this);
        }

        @Override
        public boolean isAllowedToTakeBreaks() {
            if (isFarmingWork()) {
                return true;
            }
            if (isBakingWork()) {
                return true;
            }
            if (isFinishingUp()) {
                return true;
            }
            if (isWorking()) {
                return true;
            }
            if (isReturning()) {
                return true;
            }
            return false;
        }

        @Override
        public IStatusFactory<Status> getFactory() {
            return FACTORY;
        }

        @Override
        public boolean isGoingToJobsite() {
            return ImmutableList.of(
                    GOING_TO_JOBSITE
            ).contains(this);
        }

        @Override
        public boolean isWorkingOnProduction() {
            return ImmutableList.of(
            ).contains(this);
        }

        @Override
        public boolean isDroppingLoot() {
            return ImmutableList.of(
                    DROPPING_LOOT
            ).contains(this);
        }

        @Override
        public boolean isCollectingSupplies() {
            return ImmutableList.of(
                    COLLECTING_SUPPLIES
            ).contains(this);
        }

        @Override
        public boolean isUnset() {
            return this == UNSET;
        }
    }

    public interface Item<I extends Item<I>> {
        boolean isEmpty();

        boolean isFood();

        I shrink();
    }

    public record Tools(boolean hasAxe, boolean hasPick, boolean hasShovel, boolean hasRod) {
        public Tools withAxe() {
            return new Tools(true, hasPick, hasShovel, hasRod);
        }

        public Tools withPickaxe() {
            return new Tools(hasAxe, true, hasShovel, hasRod);
        }

        public Tools withShovel() {
            return new Tools(hasAxe, hasPick, true, hasRod);
        }

        public Tools withFishingRod() {
            return new Tools(hasAxe, hasPick, hasShovel, true);
        }
    }

    public interface LootProvider<I extends GathererJournal.Item<I>> {
        Collection<I> getLoot(Tools tools);
    }

    public record Snapshot<H extends HeldItem<H, ?> & Item<H>>(Status status,
                                                               ImmutableList<H> items) implements ca.bradj.questown.jobs.Snapshot<H> {

        public static final String NAME = "gatherer";

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

        @Override
        public String statusStringValue() {
            return status.name();
        }

        @Override
        public String jobStringValue() {
            return NAME;
        }
    }
}
