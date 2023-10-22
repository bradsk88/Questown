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
            SmelterStatus.WORK_INSERTING_COAL, true,
            SmelterStatus.WORK_INSERTING_ORE, true
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
    ) implements JobTownProvider<SmelterStatus, Room> {
    }

    private record ConstEntity(
            @Nullable RoomRecipeMatch<Room> siteWhereEntityIs
    ) implements EntityLocStateProvider<Room> {
        @Override
        public @Nullable RoomRecipeMatch<Room> getEntityCurrentJobSite() {
            return siteWhereEntityIs;
        }
    }

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