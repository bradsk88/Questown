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

class SmelterStatusesTest {
    public static final ImmutableMap<SmelterStatus, Boolean> HAS_ALL_SUPPLIES = ImmutableMap.of(
            SmelterStatus.GOING_TO_JOBSITE, true,
            SmelterStatus.INSERTING_COAL, true,
            SmelterStatus.INSERTING_ORE, true
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
            Map<SmelterStatus, Boolean> getSupplyItemStatus
    ) implements EntityInvStateProvider<SmelterStatus> {
    }

    private record ConstTown(
            boolean hasSupplies,
            boolean hasSpace,
            Collection<RoomRecipeMatch<Room>> fullBakeries,
            Collection<Room> roomsWithCompletedProduct,
            Map<SmelterStatus, ? extends Collection<Room>> roomsNeedingIngredients
    ) implements SmelterStatuses.TownStateProvider<Room> {
    }

    private record ConstEntity(
            @Nullable RoomRecipeMatch<Room> siteWhereEntityIs
    ) implements EntityLocStateProvider<Room> {
        @Override
        public @Nullable RoomRecipeMatch<Room> getEntityCurrentJobSite() {
            return siteWhereEntityIs;
        }
    }

    @Test
    void InMorning_StatusShouldStay_GoingToJobSite_WhenEntityIsNotInJobSite_AndHasSupplies() {
        SmelterStatus s = SmelterStatuses.getNewStatusFromSignal(
                SmelterStatus.GOING_TO_JOBSITE,
                Signals.MORNING,
                new ConstInventory(false, false, true, HAS_ALL_SUPPLIES),
                new ConstTown(
                        true, true,
                        ImmutableList.of(),
                        ImmutableList.of(),
                        ImmutableMap.of(
                                SmelterStatus.INSERTING_ORE, ImmutableList.of(arbitraryRoom.room),
                                SmelterStatus.INSERTING_COAL, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new ConstEntity(null)
        );
        Assertions.assertNull(s); // Null means "unchanged"
    }

    @Test
    void InMorning_StatusShouldBe_Idle_WhenAllSitesFull_AndInJobSite() {
        SmelterStatus s = SmelterStatuses.getNewStatusFromSignal(
                SmelterStatus.PROCESSING_ORE,
                Signals.MORNING,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(
                        true, true,
                        ImmutableList.of(arbitraryRoom),
                        ImmutableList.of(), // <- None have smelted ingots
                        ImmutableMap.of(
                            // Rooms don't need anything
                            // SmelterStatus.INSERTING_ORE, null,
                            // SmelterStatus.INSERTING_COAL, null
                        )
                ),
                new ConstEntity(arbitraryRoom) // <- Entity is in a job site
        );
        Assertions.assertEquals(SmelterStatus.IDLE, s);
    }

    @Test
    void InMorning_StatusShouldBe_Idle_WhenAllSitesFull_AndOutOfJobSite() {
        SmelterStatus s = SmelterStatuses.getNewStatusFromSignal(
                SmelterStatus.PROCESSING_ORE,
                Signals.MORNING,
                new ConstInventory(false, false, false, ImmutableMap.of()),
                new ConstTown(
                        true, true,
                        ImmutableList.of(arbitraryRoom),
                        ImmutableList.of(), // <- None have smelted ingots
                        ImmutableMap.of(
                                // Rooms don't need anything
                                // SmelterStatus.INSERTING_ORE, null,
                                // SmelterStatus.INSERTING_COAL, null
                        )
                ),
                new ConstEntity(null) // <- Not in a job site
        );
        Assertions.assertEquals(SmelterStatus.IDLE, s);
    }

    @Test
    void InMorning_StatusShouldBe_INSERTING_ORE_WhenEntityIsInJobSite_AndJobSiteNeedsOre() {
        SmelterStatus s = SmelterStatuses.getNewStatusFromSignal(
                SmelterStatus.GOING_TO_JOBSITE,
                Signals.MORNING,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(
                        true, true,
                        ImmutableList.of(),
                        ImmutableList.of(),
                        ImmutableMap.of(
                                // Job site needs ore
                                SmelterStatus.INSERTING_ORE, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new ConstEntity(arbitraryRoom)
        );
        Assertions.assertEquals(SmelterStatus.INSERTING_ORE, s);
    }

    @Test
    void InMorning_StatusShouldBe_INSERTING_COAL_WhenEntityIsInJobSite_AndJobSiteNeedsCoal() {
        SmelterStatus s = SmelterStatuses.getNewStatusFromSignal(
                SmelterStatus.GOING_TO_JOBSITE,
                Signals.MORNING,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(
                        true, true,
                        ImmutableList.of(),
                        ImmutableList.of(),
                        ImmutableMap.of(
                                // Job site needs coal
                                SmelterStatus.INSERTING_COAL, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new ConstEntity(arbitraryRoom)
        );
        Assertions.assertEquals(SmelterStatus.INSERTING_COAL, s);
    }

    @Test
    void InMorning_StatusShouldBe_DroppingLoot_WhenEntityIsInJobSite_AndInventoryIsOnlyOre_AndAllBlocksFullOfOre() {
        boolean hasSupplies = true;
        SmelterStatus s = SmelterStatuses.getNewStatusFromSignal(
                SmelterStatus.GOING_TO_JOBSITE,
                Signals.MORNING,
                new ConstInventory(false, true, false, ImmutableMap.of(
                        SmelterStatus.INSERTING_ORE, true, // <- Has ore
                        SmelterStatus.INSERTING_COAL, false // <- Has no coal
                )),
                new ConstTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableList.of(),
                        ImmutableMap.of(
                                // Job site needs coal (not ore)
                                SmelterStatus.INSERTING_COAL, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new ConstEntity(arbitraryRoom)
        );
        Assertions.assertEquals(SmelterStatus.DROPPING_LOOT, s);
    }

    @Test
    void InMorning_StatusShouldBe_GoingToJobSite_WhenCurrentJobSiteHasNoJobs_AndAnotherJobSiteHasJobs() {
        RoomRecipeMatch<Room> currentRoom = new RoomRecipeMatch<>(
                new Room(
                        new Position(0, 1),
                        new InclusiveSpace(new Position(2, 3), new Position(4, 5))
                ),
                new ResourceLocation(Questown.MODID, "bakery"),
                ImmutableList.of()
        );

        boolean hasSupplies = true;
        SmelterStatus s = SmelterStatuses.getNewStatusFromSignal(
                SmelterStatus.GOING_TO_JOBSITE,
                Signals.MORNING,
                new ConstInventory(false, true, false, ImmutableMap.of(
                        SmelterStatus.INSERTING_ORE, true, // <- Has ore
                        SmelterStatus.INSERTING_COAL, false // <- Has no coal
                )),
                new ConstTown(
                        hasSupplies, true,
                        ImmutableList.of(),
                        ImmutableList.of(),
                        ImmutableMap.of(
                                // Job site needs coal (not ore)
                                SmelterStatus.INSERTING_COAL, ImmutableList.of(arbitraryRoom.room)
                        )
                ),
                new ConstEntity(currentRoom)
        );
        Assertions.assertEquals(SmelterStatus.DROPPING_LOOT, s);
    }
//
//    @Test
//    void InMorning_StatusShouldBe_Relaxing_WhenCurrentBakeryIsFull_AndNoEmptyBakeriesExist_AndTownHasSupplies_AndInventoryIsEmpty() {
//        RoomRecipeMatch<Room> currentRoom = new RoomRecipeMatch<>(
//                new Room(
//                        new Position(0, 1),
//                        new InclusiveSpace(new Position(2, 3), new Position(4, 5))
//                ),
//                new ResourceLocation(Questown.MODID, "bakery"),
//                ImmutableList.of()
//        );
//
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.GOING_TO_JOBSITE,
//                Signals.MORNING,
//                new ConstInventory(false, false, false, ImmutableMap.of()),
//                new ConstTown(
//                        true, false,
//                        ImmutableList.of(currentRoom),
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of()
//                ),
//                new ConstEntity(currentRoom)
//        );
//        Assertions.assertEquals(GathererJournal.Status.IDLE, s);
//    }
//
//    // TODO: Should also work if inventory is not FULL
//    @Test
//    void InMorning_StatusShouldBe_DroppingLoot_WhenNoEmptyBakeriesExist_AndInventoryIsFullOfSupplies() {
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.IDLE,
//                Signals.MORNING,
//                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of()
//                ),
//                new ConstEntity(null)
//        );
//        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
//    }
//
//
//    @Test
//    void InMorning_StatusShouldBe_FuelingOvens_IfInventoryHasCoal_AndBakeryHasWheat_ButNoCoal() {
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.IDLE,
//                Signals.MORNING,
//                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(), ImmutableList.of(),
//                        ImmutableList.of(arbitraryRoom.room), // < A room needs coal
//                        ImmutableList.of()
//                ),
//                new ConstEntity(arbitraryRoom)
//        );
//        Assertions.assertEquals(GathererJournal.Status.BAKING_FUELING, s);
//    }
//
//    @Test
//    void InMorning_StatusShouldPrefer_CollectingBread_EvenIfBakeryNeedsCoal() {
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.IDLE,
//                Signals.MORNING,
//                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(),
//                        ImmutableList.of(arbitraryRoom.room), // <- A room has bread
//                        ImmutableList.of(arbitraryRoom.room), // <- A room needs coal
//                        ImmutableList.of()
//                ),
//                new ConstEntity(arbitraryRoom)
//        );
//        Assertions.assertEquals(GathererJournal.Status.BAKING_FUELING, s);
//    }
//
//    @Test
//    void InMorning_StatusShouldPreferCollectingSupplies_OverBakingOrFueling_IfInventoryIsNotFull_AndNotInBakery() {
//        boolean inventoryFull = false;
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.IDLE,
//                Signals.MORNING,
//                new ConstInventory(inventoryFull, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(arbitraryRoom.room), // <- A room needs coal
//                        ImmutableList.of()
//                ),
//                new ConstEntity(null)
//        );
//        Assertions.assertEquals(GathererJournal.Status.COLLECTING_SUPPLIES, s);
//    }
//
//    @Test
//    void InMorning_StatusShouldPrefer_Fueling_OverCollectingSupplies_IfInventoryIsNotFull_AndInBakery() {
//        boolean inventoryFull = false;
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.IDLE,
//                Signals.MORNING,
//                new ConstInventory(inventoryFull, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(arbitraryRoom.room), // <- A room needs coal
//                        ImmutableList.of()
//                ),
//                new ConstEntity(arbitraryRoom)
//        );
//        Assertions.assertEquals(GathererJournal.Status.BAKING_FUELING, s);
//    }
//
//    @Test
//    void InMorning_StatusShouldPrefer_Baking_OverCollectingSupplies_IfInventoryIsNotFull_AndInBakery() {
//        boolean inventoryFull = false;
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.IDLE,
//                Signals.MORNING,
//                new ConstInventory(inventoryFull, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(arbitraryRoom.room) // <- A room needs wheat
//                ),
//                new ConstEntity(arbitraryRoom)
//        );
//        Assertions.assertEquals(GathererJournal.Status.BAKING, s);
//    }
//
//    @Test
//    void InMorning_StatusShouldPreferCollectingBread_OverCollectingSuppliesAndBakingOrFueling_IfInventoryIsNotFull_AndNotInBakery() {
//        boolean inventoryFull = false;
//        GathererJournal.Status s = BakerStatuses.getNewStatusFromSignal(
//                GathererJournal.Status.IDLE,
//                Signals.MORNING,
//                new ConstInventory(inventoryFull, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(),
//                        ImmutableList.of(arbitraryRoom.room), // <- A room has bread
//                        ImmutableList.of(arbitraryRoom.room), // <- A room needs coal
//                        ImmutableList.of(arbitraryRoom.room) // <- A room needs wheat
//                ),
//                new ConstEntity(null)
//        );
//        Assertions.assertEquals(GathererJournal.Status.COLLECTING_BREAD, s);
//    }
//
//    @Test
//    void InEvening_StatusShouldBe_Relaxing_IfInventoryEmpty() {
//        GathererJournal.Status s = BakerStatuses.getEveningStatus(
//                GathererJournal.Status.IDLE,
//                new ConstInventory(false, false, false, ImmutableMap.of()),
//                new ConstTown(
//                        true, false,
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of()
//                )
//        );
//        Assertions.assertEquals(GathererJournal.Status.RELAXING, s);
//    }
//
//    @Test
//    void InEvening_StatusShouldBe_Dropping_IfInventory_HasNonSupplyItems() {
//        GathererJournal.Status s = BakerStatuses.getEveningStatus(
//                GathererJournal.Status.IDLE,
//                new ConstInventory(false, true, true, ImmutableMap.of()),
//                new ConstTown(
//                        true, false,
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of()
//                )
//        );
//        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
//    }
//
//    @Test
//    void InEvening_StatusShouldBe_Dropping_IfInventory_HasSupplyItems() {
//        GathererJournal.Status s = BakerStatuses.getEveningStatus(
//                GathererJournal.Status.IDLE,
//                new ConstInventory(false, true, false, HAS_ALL_SUPPLIES),
//                new ConstTown(
//                        true, false,
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of(),
//                        ImmutableList.of()
//                )
//        );
//        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, s);
//    }
//
//    @Test
//    void InEvening_StatusShouldBe_CollectingBread_IfInventoryHasSpace_AndBakeryHasBread() {
//        GathererJournal.Status s = BakerStatuses.getEveningStatus(
//                GathererJournal.Status.IDLE,
//                new ConstInventory(false, false, false, ImmutableMap.of()),
//                new ConstTown(
//                        true, true,
//                        ImmutableList.of(),
//                        ImmutableList.of(arbitraryRoom.room),
//                        ImmutableList.of(),
//                        ImmutableList.of()
//                )
//        );
//        Assertions.assertEquals(GathererJournal.Status.COLLECTING_BREAD, s);
//    }
}