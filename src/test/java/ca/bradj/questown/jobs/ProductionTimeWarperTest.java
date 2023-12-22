package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.interfaces.TimerHandle;
import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.ProductionTimeWarper.dropIntoContainers;

class ProductionTimeWarperTest {

    @Test
    void simulateExtractProduct_shouldRemoveItemFromTown() {

        ArrayList<String> townItems = new ArrayList<>();
        townItems.add("bread");

        ProductionTimeWarper.<TestItem, TestItem>simulateExtractProduct(
                ProductionStatus.EXTRACTING_PRODUCT,
                ImmutableList.of(
                        new TestItem("") // 1-slot inventory with nothing in it
                ),
                item -> item,
                () -> new TestItem(townItems.remove(0))
        );
        Assertions.assertIterableEquals(ImmutableList.of(), townItems);
    }

    @Test
    void simulateExtractProduct_shouldAddItemToInventory() {

        ArrayList<String> townItems = new ArrayList<>();
        townItems.add("bread");

        ProductionTimeWarper.Result<TestItem> res = ProductionTimeWarper.<TestItem, TestItem>
                simulateExtractProduct(
                ProductionStatus.EXTRACTING_PRODUCT,
                ImmutableList.of(
                        new TestItem("") // 1-slot inventory with nothing in it
                ),
                item -> item,
                () -> new TestItem(townItems.remove(0))
        );
        Assertions.assertIterableEquals(ImmutableList.of(
                new TestItem("bread")
        ), res.items());
    }

    @Test
    void simulateExtractProduct_shouldThrow_IfInventoryIsFull() {
        ArrayList<String> townItems = new ArrayList<>();
        townItems.add("bread");

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> {
                    ProductionTimeWarper.Result<TestItem> res = ProductionTimeWarper.<TestItem, TestItem>
                            simulateExtractProduct(
                            ProductionStatus.EXTRACTING_PRODUCT,
                            ImmutableList.of(
                                    new TestItem("bread") // 1-slot inventory with bread in it
                            ),
                            item -> item,
                            () -> new TestItem(townItems.remove(0))
                    );
                }
        );
    }

    @Test
    void simulateDropLoot_shouldThrow_IfInventoryIsEmpty() {
        ArrayList<String> townItems = new ArrayList<>();
        townItems.add("bread");

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> {
                    ProductionTimeWarper.Result<TestItem> res = ProductionTimeWarper.<TestItem, TestItem>
                            simulateDropLoot(
                            new ProductionTimeWarper.Result<>(
                                    ProductionStatus.DROPPING_LOOT,
                                    ImmutableList.of(
                                            new TestItem("") // 1-slot inventory with nothing in it
                                    )
                            ),
                            item -> item,
                            () -> new TestItem(townItems.remove(0))
                    );
                }
        );
    }

    @Test
    void simulateDropLoot_shouldRemoveItemFromInventory() {
        ArrayList<TestItem> townItems = new ArrayList<>();

        ProductionTimeWarper.Result<TestItem> res = ProductionTimeWarper.<TestItem, TestItem>
                simulateDropLoot(
                        new ProductionTimeWarper.Result<>(
                                ProductionStatus.DROPPING_LOOT,
                                ImmutableList.of(
                                        new TestItem("bread") // 1 slot inventory with bread in it
                                )
                        ),
                heldItems -> {
                            townItems.addAll(heldItems);
                            return ImmutableList.of(); // All items deposited - no leftovers TODO: Test leftovers
                },
                () -> new TestItem("")
        );
        Assertions.assertIterableEquals(ImmutableList.of(
                new TestItem("")
        ), res.items());
    }
    @Test
    void simulateDropLoot_shouldAddItemToTown() {
        ArrayList<TestItem> townItems = new ArrayList<>();

        ProductionTimeWarper.Result<TestItem> res = ProductionTimeWarper.<TestItem, TestItem>
                simulateDropLoot(
                        new ProductionTimeWarper.Result<>(
                                ProductionStatus.DROPPING_LOOT,
                                ImmutableList.of(
                                        new TestItem("bread") // 1 slot inventory with bread in it
                                )
                        ),
                heldItems -> {
                            townItems.addAll(heldItems);
                            return ImmutableList.of(); // All items deposited - no leftovers TODO: Test leftovers
                },
                () -> new TestItem("")
        );
        Assertions.assertIterableEquals(ImmutableList.of(
                new TestItem("bread")
        ), townItems);
    }
    @Test
    void simulateDropLoot_shouldLeaveStatusAsDropping() {
        // We'll let the next iteration compute status for us
        ArrayList<TestItem> townItems = new ArrayList<>();

        ProductionTimeWarper.Result<TestItem> res = ProductionTimeWarper.<TestItem, TestItem>
                simulateDropLoot(
                        new ProductionTimeWarper.Result<>(
                                ProductionStatus.DROPPING_LOOT,
                                ImmutableList.of(
                                        new TestItem("bread") // 1 slot inventory with bread in it
                                )
                        ),
                heldItems -> {
                            townItems.addAll(heldItems);
                            return ImmutableList.of(); // All items deposited - no leftovers TODO: Test leftovers
                },
                () -> new TestItem("")
        );
        Assertions.assertEquals(ProductionStatus.DROPPING_LOOT, res.status());
    }

    @Test
    void dropIntoContainers_ShouldReturnAllInputItems_IfNoContainers() {
        ImmutableList<TestItem> items = ImmutableList.of(
                new TestItem("bread")
        );
        @NotNull ImmutableList<ContainerTarget<ContainerTarget.Container<TestItem>, TestItem>> containers = ImmutableList.of(
                // Empty
        );
        Collection<TestItem> after = dropIntoContainers(items, containers);
        Assertions.assertIterableEquals(items, after);
    }
    @Test
    void dropIntoContainers_ShouldReturnRemainingInputItems_IfOnlySomeCanFitInContainers() {
        ImmutableList<TestItem> items = ImmutableList.of(
                new TestItem("bread"),
                new TestItem("bread")
        );
        int containerCapacity = 1;
        @NotNull ImmutableList<ContainerTarget<ContainerTarget.Container<TestItem>, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(1, 2), 3,
                        new Position(2, 2),
                        new TestContainer<>(containerCapacity, () -> new TestItem("")),
                        () -> true
                )
        );
        Collection<TestItem> after = dropIntoContainers(items, containers);
        Assertions.assertIterableEquals(ImmutableList.of(
                new TestItem("bread") // Only room to insert one of the breads. So one remains.
        ), after);
    }
    @Test
    void dropIntoContainers_ShouldReturnNoItems_IfAllCanFitInContainers() {
        ImmutableList<TestItem> items = ImmutableList.of(
                new TestItem("bread"),
                new TestItem("bread")
        );
        int containerCapacity = 2;
        @NotNull ImmutableList<ContainerTarget<ContainerTarget.Container<TestItem>, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(1, 2), 3,
                        new Position(2, 2),
                        new TestContainer<>(containerCapacity, () -> new TestItem("")),
                        () -> true
                )
        );
        Collection<TestItem> after = dropIntoContainers(items, containers);
        Assertions.assertIterableEquals(ImmutableList.of(
                // Empty
        ), after);
    }
    @Test
    void dropIntoContainers_ShouldReturnNoItems_IfExcessRoomInContainers() {
        ImmutableList<TestItem> items = ImmutableList.of(
                new TestItem("bread"),
                new TestItem("bread")
        );
        int containerCapacity = 3;
        @NotNull ImmutableList<ContainerTarget<ContainerTarget.Container<TestItem>, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(1, 2), 3,
                        new Position(2, 2),
                        new TestContainer<>(containerCapacity, () -> new TestItem("")),
                        () -> true
                )
        );
        Collection<TestItem> after = dropIntoContainers(items, containers);
        Assertions.assertIterableEquals(ImmutableList.of(
                // Empty
        ), after);
    }
    @Test
    void dropIntoContainers_ShouldReturnNoItems_IfRoomAcrossMultipleContainers() {
        ImmutableList<TestItem> items = ImmutableList.of(
                new TestItem("bread"),
                new TestItem("bread")
        );
        int containerCapacity = 1;
        int numContainers = 2;
        @NotNull ImmutableList<ContainerTarget<ContainerTarget.Container<TestItem>, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(1, 2), 3,
                        new Position(2, 2),
                        new TestContainer<>(containerCapacity, () -> new TestItem("")),
                        () -> true
                ),
                new ContainerTarget<>(
                        new Position(1, 2), 3,
                        new Position(2, 2),
                        new TestContainer<>(containerCapacity, () -> new TestItem("")),
                        () -> true
                )
        );
        Collection<TestItem> after = dropIntoContainers(items, containers);
        Assertions.assertIterableEquals(ImmutableList.of(
                // Empty
        ), after);
    }
    @Test
    void dropIntoContainers_ShouldReturnRemainingInputItems_IfNotEnoughRoom_AcrossMultipleContainers() {
        ImmutableList<TestItem> items = ImmutableList.of(
                new TestItem("bread"),
                new TestItem("bread"),
                new TestItem("bread")
        );
        int containerCapacity = 1;
        @NotNull ImmutableList<ContainerTarget<ContainerTarget.Container<TestItem>, TestItem>> containers = ImmutableList.of(
                new ContainerTarget<>(
                        new Position(1, 2), 3,
                        new Position(2, 2),
                        new TestContainer<>(containerCapacity, () -> new TestItem("")),
                        () -> true
                ),
                new ContainerTarget<>(
                        new Position(1, 2), 3,
                        new Position(2, 2),
                        new TestContainer<>(containerCapacity, () -> new TestItem("")),
                        () -> true
                )
        );
        Collection<TestItem> after = dropIntoContainers(items, containers);
        Assertions.assertIterableEquals(ImmutableList.of(
                new TestItem("bread") // 3 input items, 2 containers each of size 1 -> one bread remains
        ), after);
    }

    public static class TestContainer<I extends Item<I>> implements ContainerTarget.@NotNull Container<I> {
        ArrayList<I> delegate = new ArrayList<>();
        int size;
        public TestContainer(int i, Supplier<I> empty) {
            this.size = i;
            delegate.addAll(Collections.nCopies(i, empty.get()));
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public I getItem(int i) {
            return delegate.get(i);
        }

        @Override
        public boolean hasAnyOf(ImmutableSet<I> items) {
            for (I item : items) {
                if (delegate.contains(item)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void setItems(List<I> newItems) {
            delegate.clear();
            delegate.addAll(newItems);
        }

        @Override
        public void removeItem(int index, int amount) {
            delegate.remove(index);
        }

        @Override
        public void setItem(int i, I item) {
            delegate.set(i, item);
        }

        @Override
        public boolean isFull() {
            return delegate.stream().noneMatch(Item::isEmpty);
        }

        @Override
        public String toShortString() {
            return delegate.toString();
        }
    }
}