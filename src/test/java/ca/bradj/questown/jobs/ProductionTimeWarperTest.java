package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.interfaces.TimerHandle;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

class ProductionTimeWarperTest {

    @Test
    void constructorShouldNotThrow() {
        // This essentially tests that all the production statuses have been handled
        new ProductionTimeWarper(
                (status) -> new GathererJournalTest.TestItem("bread"),
                new ProductionTimeWarper.LootGiver() {
                    @Override
                    public @NotNull Iterable giveLoot(int max, GathererJournal.Tools tools, Object o) {
                        return ImmutableList.of();
                    }
                },
                new ProductionTimeWarper.Town() {
                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return null;
                    }

                    @Override
                    public boolean IsStorageAvailable() {
                        return false;
                    }

                    @Override
                    public boolean hasGate() {
                        return false;
                    }
                },
                new TimerHandle() {
                    @Override
                    public void tick(Object o, Collection roomsToScanForChanges) {
                        // TODO: Test timers
                    }
                },
        new EmptyFactory() {
                    @Override
                    public Item makeEmptyItem() {
                        return new GathererJournalTest.TestItem("");
                    }
                },
                new ProductionTimeWarper.ItemToEntityMover() {
                    @Override
                    public HeldItem copyFromTownWithoutRemoving(Item item) {
                        return new GathererJournalTest.TestItem("bread");
                    }
                },
                new GathererJournal.ToolsChecker() {
                    @Override
                    public GathererJournal.Tools computeTools(Iterable items) {
                        return new GathererJournal.Tools(false, false, false, false);
                    }
                },
                (heldItems) -> null
        );
    }

    @Test
    void warp_should

    @Test
    void simulateExtractProduct_shouldRemoveItemFromTown() {

        ArrayList<String> townItems = new ArrayList<>();
        townItems.add("bread");

        ProductionTimeWarper.<GathererJournalTest.TestItem, GathererJournalTest.TestItem>simulateExtractProduct(
                ProductionStatus.EXTRACTING_PRODUCT,
                ImmutableList.of(
                        new GathererJournalTest.TestItem("") // 1-slot inventory with nothing in it
                ),
                item -> item,
                () -> new GathererJournalTest.TestItem(townItems.remove(0))
        );
        Assertions.assertIterableEquals(ImmutableList.of(), townItems);
    }

    @Test
    void simulateExtractProduct_shouldAddItemToInventory() {

        ArrayList<String> townItems = new ArrayList<>();
        townItems.add("bread");

        ProductionTimeWarper.WarpResult<GathererJournalTest.TestItem> res = ProductionTimeWarper.<GathererJournalTest.TestItem, GathererJournalTest.TestItem>
                simulateExtractProduct(
                ProductionStatus.EXTRACTING_PRODUCT,
                ImmutableList.of(
                        new GathererJournalTest.TestItem("") // 1-slot inventory with nothing in it
                ),
                item -> item,
                () -> new GathererJournalTest.TestItem(townItems.remove(0))
        );
        Assertions.assertIterableEquals(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ), res.items());
    }

    @Test
    void simulateExtractProduct_shouldThrow_IfInventoryIsFull() {
        ArrayList<String> townItems = new ArrayList<>();
        townItems.add("bread");

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> {
                    ProductionTimeWarper.WarpResult<GathererJournalTest.TestItem> res = ProductionTimeWarper.<GathererJournalTest.TestItem, GathererJournalTest.TestItem>
                            simulateExtractProduct(
                            ProductionStatus.EXTRACTING_PRODUCT,
                            ImmutableList.of(
                                    new GathererJournalTest.TestItem("bread") // 1-slot inventory with bread in it
                            ),
                            item -> item,
                            () -> new GathererJournalTest.TestItem(townItems.remove(0))
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
                    ProductionTimeWarper.WarpResult<GathererJournalTest.TestItem> res = ProductionTimeWarper.<GathererJournalTest.TestItem, GathererJournalTest.TestItem>
                            simulateDropLoot(
                            new ProductionTimeWarper.WarpResult<>(
                                    ProductionStatus.DROPPING_LOOT,
                                    ImmutableList.of(
                                            new GathererJournalTest.TestItem("") // 1-slot inventory with nothing in it
                                    )
                            ),
                            item -> item,
                            () -> new GathererJournalTest.TestItem(townItems.remove(0))
                    );
                }
        );
    }

    @Test
    void simulateDropLoot_shouldRemoveItemFromInventory() {
        ArrayList<GathererJournalTest.TestItem> townItems = new ArrayList<>();

        ProductionTimeWarper.WarpResult<GathererJournalTest.TestItem> res = ProductionTimeWarper.<GathererJournalTest.TestItem, GathererJournalTest.TestItem>
                simulateDropLoot(
                        new ProductionTimeWarper.WarpResult<>(
                                ProductionStatus.DROPPING_LOOT,
                                ImmutableList.of(
                                        new GathererJournalTest.TestItem("bread") // 1 slot inventory with bread in it
                                )
                        ),
                heldItems -> {
                            townItems.addAll(heldItems);
                            return ImmutableList.of(); // All items deposited - no leftovers TODO: Test leftovers
                },
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertIterableEquals(ImmutableList.of(
                new GathererJournalTest.TestItem("")
        ), res.items());
    }
    @Test
    void simulateDropLoot_shouldAddItemToTown() {
        ArrayList<GathererJournalTest.TestItem> townItems = new ArrayList<>();

        ProductionTimeWarper.WarpResult<GathererJournalTest.TestItem> res = ProductionTimeWarper.<GathererJournalTest.TestItem, GathererJournalTest.TestItem>
                simulateDropLoot(
                        new ProductionTimeWarper.WarpResult<>(
                                ProductionStatus.DROPPING_LOOT,
                                ImmutableList.of(
                                        new GathererJournalTest.TestItem("bread") // 1 slot inventory with bread in it
                                )
                        ),
                heldItems -> {
                            townItems.addAll(heldItems);
                            return ImmutableList.of(); // All items deposited - no leftovers TODO: Test leftovers
                },
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertIterableEquals(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ), townItems);
    }
    @Test
    void simulateDropLoot_shouldLeaveStatusAsDropping() {
        // We'll let the next iteration compute status for us
        ArrayList<GathererJournalTest.TestItem> townItems = new ArrayList<>();

        ProductionTimeWarper.WarpResult<GathererJournalTest.TestItem> res = ProductionTimeWarper.<GathererJournalTest.TestItem, GathererJournalTest.TestItem>
                simulateDropLoot(
                        new ProductionTimeWarper.WarpResult<>(
                                ProductionStatus.DROPPING_LOOT,
                                ImmutableList.of(
                                        new GathererJournalTest.TestItem("bread") // 1 slot inventory with bread in it
                                )
                        ),
                heldItems -> {
                            townItems.addAll(heldItems);
                            return ImmutableList.of(); // All items deposited - no leftovers TODO: Test leftovers
                },
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(ProductionStatus.DROPPING_LOOT, res.status());
    }
}