package ca.bradj.questown.jobs.scenarios;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.*;
import java.util.function.Supplier;

/**
 * Test TownState implementation for scenario testing.
 * This class provides a complete, testable TownState without Minecraft dependencies.
 */
public class TestTownState extends TownState<TestTownState.TestContainer, TestItem, TestItem, Position, TestTownState> {

    private final ImmutableList<TestItem> insertedItems;
    private int cyclesCompleted = 0;
    private int foodConsumed = 0;

    public TestTownState(
            List<VillagerData<TestItem>> villagers,
            List<ContainerTarget<TestContainer, TestItem>> containers,
            ImmutableMap<Position, State> workStates,
            ImmutableMap<Position, Integer> workTimers,
            List<Position> gates,
            ImmutableMap<UUID, Boolean> blocksOfProgress,
            long worldTimeAtSleep
    ) {
        super(villagers, containers, workStates, workTimers, gates, blocksOfProgress, worldTimeAtSleep);
        this.insertedItems = ImmutableList.of();
    }

    private TestTownState(
            List<VillagerData<TestItem>> villagers,
            List<ContainerTarget<TestContainer, TestItem>> containers,
            ImmutableMap<Position, State> workStates,
            ImmutableMap<Position, Integer> workTimers,
            List<Position> gates,
            ImmutableMap<UUID, Boolean> blocksOfProgress,
            long worldTimeAtSleep,
            ImmutableList<TestItem> insertedItems,
            int cyclesCompleted,
            int foodConsumed
    ) {
        super(villagers, containers, workStates, workTimers, gates, blocksOfProgress, worldTimeAtSleep);
        this.insertedItems = insertedItems;
        this.cyclesCompleted = cyclesCompleted;
        this.foodConsumed = foodConsumed;
    }

    @Override
    protected TestTownState newTownState(
            ImmutableList<VillagerData<TestItem>> villagers,
            ImmutableList<ContainerTarget<TestContainer, TestItem>> containers,
            ImmutableMap<Position, State> workStates,
            ImmutableMap<Position, Integer> workTimers,
            ImmutableList<Position> gates,
            ImmutableMap<UUID, Boolean> blocksOfProgress,
            long worldTimeAtSleep
    ) {
        return new TestTownState(
                villagers,
                new ArrayList<>(containers),
                workStates,
                workTimers,
                new ArrayList<>(gates),
                blocksOfProgress,
                worldTimeAtSleep,
                this.insertedItems,
                this.cyclesCompleted,
                this.foodConsumed
        );
    }

    public ImmutableList<TestItem> getInsertedItems() {
        return insertedItems;
    }

    public TestTownState withInsertedItem(TestItem item) {
        ImmutableList.Builder<TestItem> builder = ImmutableList.builder();
        builder.addAll(insertedItems);
        builder.add(item);
        return new TestTownState(
                villagers,
                new ArrayList<>(containers),
                workStates,
                workTimers,
                new ArrayList<>(gates),
                blocksOfProgress,
                worldTimeAtSleep,
                builder.build(),
                cyclesCompleted,
                foodConsumed
        );
    }

    public TestTownState withCycleCompleted() {
        return new TestTownState(
                villagers,
                new ArrayList<>(containers),
                workStates,
                workTimers,
                new ArrayList<>(gates),
                blocksOfProgress,
                worldTimeAtSleep,
                insertedItems,
                cyclesCompleted + 1,
                foodConsumed
        );
    }

    public TestTownState withFoodConsumed(int amount) {
        return new TestTownState(
                villagers,
                new ArrayList<>(containers),
                workStates,
                workTimers,
                new ArrayList<>(gates),
                blocksOfProgress,
                worldTimeAtSleep,
                insertedItems,
                cyclesCompleted,
                foodConsumed + amount
        );
    }

    public int getCyclesCompleted() {
        return cyclesCompleted;
    }

    public int getFoodConsumed() {
        return foodConsumed;
    }

    /**
     * Counts items matching the given value across all containers.
     */
    public int countItems(String itemValue) {
        int count = 0;
        for (ContainerTarget<TestContainer, TestItem> container : containers) {
            for (int i = 0; i < container.size(); i++) {
                TestItem item = container.getItem(i);
                if (item != null && itemValue.equals(item.value)) {
                    count++;
                }
            }
        }
        return count;
    }

    // --- Static Factory Methods ---

    /**
     * Creates an empty test town.
     */
    public static TestTownState empty() {
        return new TestTownState(
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableList.of(),
                ImmutableMap.of(),
                0L
        );
    }

    /**
     * Creates a test town with a single container containing the given items.
     */
    public static TestTownState withContainer(Map<String, Integer> items) {
        TestContainer container = new TestContainer(27, TestTownState::emptyItem);
        int slot = 0;
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                container.setItemAt(slot++, new TestItem(entry.getKey()));
            }
        }
        ContainerTarget<TestContainer, TestItem> target = containerAt(new Position(0, 0), container);
        return new TestTownState(
                ImmutableList.of(),
                ImmutableList.of(target),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableList.of(),
                ImmutableMap.of(),
                0L
        );
    }

    public static TestItem emptyItem() {
        return new TestItem("");
    }

    public static ContainerTarget<TestContainer, TestItem> containerAt(Position pos, TestContainer container) {
        return new ContainerTarget<>(
                pos, 1,
                new Position(pos.x + 1, pos.z),
                container,
                () -> true,
                item -> {},
                i -> true,
                RankBoost.SAME_AS_VANILLA_CHEST.value()
        );
    }

    // --- TestContainer Implementation ---

    /**
     * Test container implementation backed by an ArrayList.
     */
    public static class TestContainer implements ContainerTarget.Container<TestItem> {
        private final List<TestItem> items;
        private final Supplier<TestItem> emptyItem;

        public TestContainer(int size, Supplier<TestItem> emptyItem) {
            this.emptyItem = emptyItem;
            this.items = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                items.add(emptyItem.get());
            }
        }

        @Override
        public int size() {
            return items.size();
        }

        @Override
        public TestItem getItem(int i) {
            return items.get(i);
        }

        @Override
        public RankBoost getItemAcceptanceRankBoost() {
            return RankBoost.SAME_AS_VANILLA_CHEST;
        }

        @Override
        public boolean canAcceptIfSpaceAllows(TestItem item) {
            return true;
        }

        @Override
        public TestItem removeItem(int index) {
            TestItem old = items.get(index);
            if (old.isEmpty()) {
                return null;
            }
            items.set(index, emptyItem.get());
            return old;
        }

        @Override
        public boolean setItem(int i, TestItem item) {
            if (items.get(i).isEmpty()) {
                items.set(i, item);
                return true;
            }
            return false;
        }

        @Override
        public boolean isFull() {
            return items.stream().noneMatch(TestItem::isEmpty);
        }

        @Override
        public String toShortString() {
            return items.toString();
        }

        @Override
        public String toShortString(boolean includeAir) {
            return toShortString();
        }

        public void setItemAt(int index, TestItem item) {
            items.set(index, item);
        }
    }
}
