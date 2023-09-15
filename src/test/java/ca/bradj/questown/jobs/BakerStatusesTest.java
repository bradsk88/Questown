package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;

class BakerStatusesTest {
    Room arbitraryRoom = new Room(
            new Position(0, 0),
            new InclusiveSpace(new Position(0, 0), new Position(1, 1))
    );

    private record ConstInventory(
            boolean inventoryFull,
            boolean hasNonSupplyItems
    ) implements BakerStatuses.InventoryStateProvider {
    }

    private record ConstTown(
            boolean hasSupplies,
            boolean hasBakerySpace,
            Collection<Room> fullBakeries
    ) implements BakerStatuses.TownStateProvider<Room> {
        @Override
        public boolean isBakeryFull(Room room) {
            return fullBakeries.contains(room);
        }
    }

    private record ConstEntity(
            @Nullable Room bakeryWhereEntityIs
    ) implements BakerStatuses.EntityStateProvider<Room> {
        @Override
        public @Nullable Room getEntityBakeryLocation() {
            return bakeryWhereEntityIs;
        }
    }

    @Test
    void InMorning_StatusShouldBe_CollectingSupplies_WhenInvEmptyTownFull() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(false, false),
                new ConstTown(true, true, ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.COLLECTING_SUPPLIES, s);
    }

    @Test
    void InMorning_StatusShouldBe_GoingToBakery_WhenInvFullOfSuppliesTownEmpty() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(true, false),
                new ConstTown(false, true, ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.GOING_TO_BAKERY, s);
    }

    @Test
    void InMorning_StatusShouldBe_NoSupplies_WhenInvEmptyTownEmpty() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(false, false),
                new ConstTown(false, true, ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.NO_SUPPLIES, s);
    }

    @Test
    void InMorning_StatusShouldBe_GoingToBakery_WhenInvFullTownFull() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(true, false),
                new ConstTown(true, true, ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.GOING_TO_BAKERY, s);
    }

    @Test
    void InMorning_StatusShouldBe_DroppingLoot_WhenInvHasNonSupplies() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(false, true),
                new ConstTown(true, true, ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
    }

    @Test
    void InMorning_StatusShouldStay_GoingToBakery_WhenEntityIsNotInBakery() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(true, false),
                new ConstTown(true, true, ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertNull(s); // Null means "unchanged"
    }

    @Test
    void InMorning_StatusShouldBe_Baking_WhenEntityIsInBakery() {

        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(true, false),
                new ConstTown(true, true, ImmutableList.of()),
                new ConstEntity(arbitraryRoom)
        );
        Assertions.assertEquals(GathererJournal.Status.BAKING, s);
    }

    @Test
    void InMorning_StatusShouldBe_GoingToBakery_WhenCurrentBakeryIsFull_AndAnotherBakeryHasAvailableOvens() {
        Room currentRoom = new Room(
                new Position(0, 1),
                new InclusiveSpace(new Position(2, 3), new Position(4, 5))
        );

        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(true, false),
                new ConstTown(true, true, ImmutableList.of(currentRoom)),
                new ConstEntity(currentRoom)
        );
        Assertions.assertEquals(GathererJournal.Status.BAKING, s);
    }

    @Test
    void InMorning_StatusShouldBe_CollectingSupplies_WhenCurrentBakeryIsFull_AndNoEmptyBakeriesExist_AndTownHasSupplies_AndInventoryIsEmpty() {
        Room currentRoom = new Room(
                new Position(0, 1),
                new InclusiveSpace(new Position(2, 3), new Position(4, 5))
        );

        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(false, false),
                new ConstTown(true, false, ImmutableList.of(currentRoom)),
                new ConstEntity(currentRoom)
        );
        Assertions.assertEquals(GathererJournal.Status.COLLECTING_SUPPLIES, s);
    }

    // TODO: Should also work if inventory is not FULL
    @Test
    void InMorning_StatusShouldBe_DroppingLoot_WhenNoEmptyBakeriesExist_AndInventoryIsFullOfSupplies() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(true, false),
                new ConstTown(true, false, ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
    }
}