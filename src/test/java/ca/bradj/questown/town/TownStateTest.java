package ca.bradj.questown.town;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownStateTest {

    private static class TestTownState extends TownState<Container, TestItem, TestItem, Position, TestTownState> {

        public TestTownState(
                @NotNull List<VillagerData<TestItem>> villagers,
                @NotNull List<ContainerTarget<Container, TestItem>> containers,
                @NotNull ImmutableMap<Position, State> workStates,
                @NotNull List<Position> gates,
                long worldTimeAtSleep
        ) {
            super(villagers, containers, workStates, ImmutableMap.of(), gates, worldTimeAtSleep);
        }

        @Override
        protected TestTownState newTownState(
                ImmutableList<VillagerData<TestItem>> villagers,
                ImmutableList<ContainerTarget<Container, TestItem>> containers,
                ImmutableMap<Position, State> workStates,
                ImmutableMap<Position, Integer> workTimers,
                ImmutableList<Position> gates,
                long worldTimeAtSleep
        ) {
            return new TestTownState(villagers, containers, workStates, gates, worldTimeAtSleep);
        }
    }

    static class Container implements ContainerTarget.Container<TestItem> {

        private final List<TestItem> items = new ArrayList<>(4);

        public Container(boolean full) {
            String item = "";
            if (full) {
                item = "something";
            }
            items.addAll(ImmutableList.of(
                    new TestItem(item),
                    new TestItem(item),
                    new TestItem(item),
                    new TestItem(item)
            ));
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public TestItem getItem(int i) {
            return items.get(i);
        }

        @Override
        public TestItem removeItem(int index) {
            TestItem out = items.get(0);
            items.set(0, new TestItem(""));
            return out;
        }

        @Override
        public boolean setItem(
                int i,
                TestItem item
        ) {
            if (items.get(i).isEmpty()) {
                items.set(i, item);
                return true;
            }
            return false;
        }

        @Override
        public boolean isFull(
        ) {
            return items.stream().noneMatch(TestItem::isEmpty);
        }

        @Override
        public String toShortString() {
            return String.join(", ", items.stream().map(v -> v.value).toList());
        }

        @Override
        public String toShortString(boolean includeAir) {
            return toShortString();
        }

        @Override
        public boolean canAcceptIfSpaceAllows(TestItem item) {
            return false;
        }

        @Override
        public RankBoost getItemAcceptanceRankBoost() {
            return null;
        }
    }

    private TestTownState townState;
    private ImmutableList<TestItem> itemsToDeposit;


    @Test
    void depositItems_shouldDepositItemsIntoContainers() {
        List<ContainerTarget<Container, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(0, 0), 0, new Position(0, 0), new Container(false), () -> true, i -> {
                }, item -> true, RankBoost.SAME_AS_VANILLA_CHEST.value()
                )
        );

        // Add villagers and containers to the TownState
        // ...

        townState = new TestTownState(new ArrayList<>(), containers, ImmutableMap.of(), new ArrayList<>(), 1234567890L);

        // Create a list of items to deposit
        itemsToDeposit = ImmutableList.of(
                new TestItem("something"),
                new TestItem("anything"),
                new TestItem("another"),
                new TestItem("thing")
        );

        ImmutableList<TestItem> remainingItems = townState.depositItems(itemsToDeposit);

        // Verify that all items were deposited
        assertEquals(0, remainingItems.size());

        // Verify that each item is in a container
        for (TestItem item : itemsToDeposit) {
            boolean foundItem = false;
            for (ContainerTarget<Container, TestItem> container : townState.containers) {
                if (container.hasItem(v -> v.equals(item))) {
                    foundItem = true;
                    break;
                }
            }
            assertTrue(foundItem, "Item should be deposited in a container");
        }
    }

    @Test
    void depositItems_shouldReturnAllInputITemsWhenNoStorageAvailable() {
        List<ContainerTarget<Container, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(0, 0), 0, new Position(0, 0), new Container(true), () -> true, i -> {
                }, item -> true, RankBoost.SAME_AS_VANILLA_CHEST.value()
                )
        );

        // Add villagers and containers to the TownState
        // ...

        townState = new TestTownState(new ArrayList<>(), containers, ImmutableMap.of(), new ArrayList<>(), 1234567890L);

        // Create a list of items to deposit
        itemsToDeposit = ImmutableList.of(
                new TestItem("something"),
                new TestItem("anything"),
                new TestItem("another"),
                new TestItem("thing")
        );
        ImmutableList<TestItem> remainingItems = townState.depositItems(itemsToDeposit);
        assertEquals(itemsToDeposit.size(), remainingItems.size());
    }

    private ImmutableList<TestItem> createFullContainerItems() {
        // Create a list of full items to fill a container
        List<TestItem> fullItems = new ArrayList<>();
        // ...

        return ImmutableList.copyOf(fullItems);
    }
}
