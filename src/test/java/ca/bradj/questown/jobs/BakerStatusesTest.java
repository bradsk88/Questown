package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

class BakerStatusesTest {
    public static final ImmutableMap<GathererJournal.Status, Boolean> HAS_ALL_SUPPLIES = ImmutableMap.of(
            GathererJournal.Status.GOING_TO_BAKERY, true,
            GathererJournal.Status.BAKING, true
    );
    RoomRecipeMatch<Room> arbitraryRoom = new RoomRecipeMatch<>(
            new Room(
                    new Position(0, 0),
                    new InclusiveSpace(new Position(0, 0), new Position(1, 1))
            ),
            new ResourceLocation(Questown.MODID, "bakery"),
            ImmutableList.of()
    );

    private record ConstInventory(
            boolean inventoryFull,
            boolean hasItems,
            boolean hasNonSupplyItems,
            Map<GathererJournal.Status, Boolean> getSupplyItemStatus
    ) implements EntityStateProvider {
    }

    private record ConstTown(
            boolean hasSupplies,
            boolean hasBakerySpace,
            Collection<RoomRecipeMatch<Room>> fullBakeries,
            Collection<Room> bakeriesWithBread
    ) implements BakerStatuses.TownStateProvider<Room> {
        @Override
        public boolean isBakeryFull(RoomRecipeMatch<Room> room) {
            return fullBakeries.contains(room);
        }
    }

    private record ConstEntity(
            @Nullable RoomRecipeMatch<Room> bakeryWhereEntityIs
    ) implements BakerStatuses.EntityStateProvider<Room> {
        @Override
        public @Nullable RoomRecipeMatch<Room> getEntityBakeryLocation() {
            return bakeryWhereEntityIs;
        }
    }

    @Test
    void InMorning_StatusShouldBe_CollectingSupplies_WhenInvEmptyTownFull() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(true, true, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.COLLECTING_SUPPLIES, s);
    }

    @Test
    void InMorning_StatusShouldBe_CollectingBread_WhenInvEmptyAndTownHasBread() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(false, true, ImmutableList.of(), ImmutableList.of(arbitraryRoom.room)),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.COLLECTING_BREAD, s);
    }

    @Test
    void InMorning_StatusShouldBe_GoingToBakery_WhenInvFullOfSuppliesTownEmpty() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(false, true, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.GOING_TO_BAKERY, s);
    }

    @Test
    void InMorning_StatusShouldBe_NoSupplies_WhenInvEmptyTownEmpty() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(false, true, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.NO_SUPPLIES, s);
    }

    @Test
    void InMorning_StatusShouldBe_GoingToBakery_WhenInvFullTownFull() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, true, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.GOING_TO_BAKERY, s);
    }

    @Test
    void InMorning_StatusShouldBe_DroppingLoot_WhenInvHasNonSupplies() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new ConstInventory(false, true, true, ImmutableMap.of()),
                new ConstTown(true, true, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
    }

    @Test
    void InMorning_StatusShouldStay_GoingToBakery_WhenEntityIsNotInBakery() {
        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, true, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertNull(s); // Null means "unchanged"
    }

    @Test
    void InMorning_StatusShouldBe_Baking_WhenEntityIsInBakery() {

        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, true, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(arbitraryRoom)
        );
        Assertions.assertEquals(GathererJournal.Status.BAKING, s);
    }

    @Test
    void InMorning_StatusShouldBe_GoingToBakery_WhenCurrentBakeryIsFull_AndAnotherBakeryHasAvailableOvens() {
        RoomRecipeMatch<Room> currentRoom = new RoomRecipeMatch<>(
                new Room(
                        new Position(0, 1),
                        new InclusiveSpace(new Position(2, 3), new Position(4, 5))
                ),
                new ResourceLocation(Questown.MODID, "bakery"),
                ImmutableList.of()
        );

        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, true, ImmutableList.of(currentRoom), ImmutableList.of()),
                new ConstEntity(currentRoom)
        );
        Assertions.assertEquals(GathererJournal.Status.BAKING, s);
    }

    @Test
    void InMorning_StatusShouldBe_CollectingSupplies_WhenCurrentBakeryIsFull_AndNoEmptyBakeriesExist_AndTownHasSupplies_AndInventoryIsEmpty() {
        RoomRecipeMatch<Room> currentRoom = new RoomRecipeMatch<>(
                new Room(
                        new Position(0, 1),
                        new InclusiveSpace(new Position(2, 3), new Position(4, 5))
                ),
                new ResourceLocation(Questown.MODID, "bakery"),
                ImmutableList.of()
        );

        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
                GathererJournal.Status.GOING_TO_BAKERY,
                Signals.MORNING,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(true, false, ImmutableList.of(currentRoom), ImmutableList.of()),
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
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, ImmutableList.of(), ImmutableList.of()),
                new ConstEntity(null)
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
    }

    @Test
    void InEvening_StatusShouldBe_Relaxing_IfInventoryEmpty() {
        GathererJournal.Status s = BakerStatuses.getEveningStatus(
                GathererJournal.Status.IDLE,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(true, false, ImmutableList.of(), ImmutableList.of())
        );
        Assertions.assertEquals(GathererJournal.Status.RELAXING, s);
    }

    @Test
    void InEvening_StatusShouldBe_Dropping_IfInventory_HasNonSupplyItems() {
        GathererJournal.Status s = BakerStatuses.getEveningStatus(
                GathererJournal.Status.IDLE,
                new ConstInventory(false, true, true, ImmutableMap.of()),
                new ConstTown(true, false, ImmutableList.of(), ImmutableList.of())
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
    }

    @Test
    void InEvening_StatusShouldBe_Dropping_IfInventory_HasSupplyItems() {
        GathererJournal.Status s = BakerStatuses.getEveningStatus(
                GathererJournal.Status.IDLE,
                new ConstInventory(false, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, ImmutableList.of(), ImmutableList.of())
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
    }

    @Test
    void InEvening_StatusShouldBe_CollectingBread_IfInventoryHasSpace_AndBakeryHasBread() {
        GathererJournal.Status s = BakerStatuses.getEveningStatus(
                GathererJournal.Status.IDLE,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(true, true, ImmutableList.of(), ImmutableList.of(
                        arbitraryRoom.room
                ))
        );
        Assertions.assertEquals(GathererJournal.Status.COLLECTING_BREAD, s);
    }
}