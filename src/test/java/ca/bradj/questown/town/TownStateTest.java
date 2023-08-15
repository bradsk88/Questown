package ca.bradj.questown.town;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.mobs.visitor.ContainerTarget;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TownStateTest {

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
        public boolean hasAnyOf(ImmutableSet<TestItem> items) {
            return false;
        }

        @Override
        public void setItems(List<TestItem> newItems) {
            items.clear();
            items.addAll(newItems);
        }

        @Override
        public void removeItem(
                int index,
                int amount
        ) {
            items.remove(items.get(index));
        }

        @Override
        public void setItem(
                int i,
                TestItem item
        ) {
            items.set(i, item);
        }

        @Override
        public boolean isFull() {
            return items.stream().noneMatch(TestItem::isEmpty);
        }
    }

    private TownState<Container, TestItem> townState;
    private ImmutableList<TestItem> itemsToDeposit;


    @Test
    void depositItems_shouldDepositItemsIntoContainers() {
        List<ContainerTarget<Container, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(0, 0), 0, new Position(0, 0), new Container(false), () -> true
                )
        );

        // Add villagers and containers to the TownState
        // ...

        townState = new TownState<>(new ArrayList<>(), containers, new ArrayList<>(), 1234567890L);

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
                        new Position(0, 0), 0, new Position(0, 0), new Container(true), () -> true
                )
        );

        // Add villagers and containers to the TownState
        // ...

        townState = new TownState<>(new ArrayList<>(), containers, new ArrayList<>(), 1234567890L);

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