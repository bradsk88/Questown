package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

class JobsCleanTest {

    public static final Predicate<Room> ONLY_CHECK_XZ_COORDINATES = (room) -> true;
    ImmutableList<Predicate<TestItem>> bakerRecipe = ImmutableList.of(
            item -> "wheat".equals(item.value),
            item -> "wheat".equals(item.value),
            item -> "coal".equals(item.value)
    );

    @Test
    void shouldTakeItem_ifInventoryEmpty_AndItemIsValidForRecipe() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "", "", "", "", "", ""
                ).stream().map(TestItem::new).toList(),
                new TestItem("wheat")
        );
        Assertions.assertTrue(result);
    }

    @Test
    void shouldNotTakeItem_ifInventoryEmpty_AndItemIsNonRecipeItem() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "", "", "", "", "", ""
                ).stream().map(TestItem::new).toList(),
                new TestItem("bomb")
        );
        Assertions.assertFalse(result);
    }

    @Test
    void shouldNotTakeItem_ifInventoryFull() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "wheat", "wheat", "coal", "wheat", "wheat", "coal"
                ).stream().map(TestItem::new).toList(),
                new TestItem("wheat")
        );
        Assertions.assertFalse(result);
    }

    @Test
    void shouldNotTakeItem_ifInventoryHasOneOpening_AndItemIsNotPerfectFIt() {
        boolean result = JobsClean.shouldTakeItem(
                6, bakerRecipe,
                ImmutableList.of(
                        "wheat", "wheat", "coal", "wheat", "wheat", "" // <-- want coal here
                ).stream().map(TestItem::new).toList(),
                new TestItem("wheat")
        );
        Assertions.assertFalse(result);
    }

    private static IRoomRecipeMatch<Room, String, Position, String> arbitaryRoomMatch1 = new IRoomRecipeMatch<Room, String, Position, String>() {
        @Override
        public ImmutableList<String> getRecipeIDs() {
            return ImmutableList.of("test 1");
        }

        @Override
        public Room getRoom() {
            return new Room(new Position(0, 0), InclusiveSpace.from(-1, 0).to(1, 2));
        }

        @Override
        public ImmutableMap<Position, String> getContainedBlocks() {
            return ImmutableMap.of(
                    new Position(0, 1), "chest"
            );
        }
    };
    public static final Position positionInsideArbitraryRoomMatch1 = arbitaryRoomMatch1.getContainedBlocks().keySet()
                                                                                       .iterator().next();


    private static IRoomRecipeMatch<Room, String, Position, String> arbitaryRoomMatch2 = new IRoomRecipeMatch<Room, String, Position, String>() {

        @Override
        public ImmutableList<String> getRecipeIDs() {
            return ImmutableList.of(getRecipeID());
        }

        public String getRecipeID() {
            return "test 1";
        }

        @Override
        public Room getRoom() {
            return new Room(new Position(100, 100), InclusiveSpace.from(100, 0).to(200, 100));
        }

        @Override
        public ImmutableMap<Position, String> getContainedBlocks() {
            return ImmutableMap.of(
                    new Position(150, 50), "chest"
            );
        }
    };

    @Test
    void roomsWithState_shouldReturnAllRoomsIfBothChecksPass() {
        ImmutableList<IRoomRecipeMatch<Room, String, Position, String>> out = JobsClean.roomsWithState(
                ImmutableList.of(arbitaryRoomMatch1),
                block -> true,
                pos -> true
        );
        Assertions.assertEquals(1, out.size());
        Assertions.assertEquals(arbitaryRoomMatch1, out.get(0));
    }

    @Test
    void roomsWithState_shouldReturnNoRoomsIfJobBlockCheckFails() {
        ImmutableList<IRoomRecipeMatch<Room, String, Position, String>> out = JobsClean.roomsWithState(
                ImmutableList.of(arbitaryRoomMatch1),
                block -> false,
                pos -> true
        );
        Assertions.assertEquals(0, out.size());
    }

    @Test
    void roomsWithState_shouldReturnNoRoomsIfStateCheckFails() {
        ImmutableList<IRoomRecipeMatch<Room, String, Position, String>> out = JobsClean.roomsWithState(
                ImmutableList.of(arbitaryRoomMatch1),
                block -> true,
                pos -> false
        );
        Assertions.assertEquals(0, out.size());
    }

    @Test
    void roomsWithState_shouldReturnNoRoomsIfBothChecksFail() {
        ImmutableList<IRoomRecipeMatch<Room, String, Position, String>> out = JobsClean.roomsWithState(
                ImmutableList.of(arbitaryRoomMatch1),
                block -> false,
                pos -> false
        );
        Assertions.assertEquals(0, out.size());
    }

    @Test
    void getEntityCurrentJobSite_shouldReturnCorrectRoom_WhenAllRequirementsEmpty_AndResultsAreAvailable_IfEntityInRoom() {
        EntityCurrentJobSite<Room> site = JobsClean.getEntityCurrentJobSite(
                positionInsideArbitraryRoomMatch1,
                new RoomsNeedingVillagerInput<Room, Object, Object>(ImmutableMap.of()),
                ImmutableList.of(arbitaryRoomMatch1.getRoom()),
                ONLY_CHECK_XZ_COORDINATES,
                (x) -> false
        );
        Assertions.assertEquals(arbitaryRoomMatch1.getRoom(), site.room());
    }

    @Test
    void getEntityCurrentJobSite_shouldReturnNull_WhenAllRequirementsEmpty_AndResultsAreAvailable_IfEntityNotInRoom() {
        EntityCurrentJobSite<Room> site = JobsClean.getEntityCurrentJobSite(
                new Position(-100, -100),
                new RoomsNeedingVillagerInput<>(ImmutableMap.of()),
                ImmutableList.of(arbitaryRoomMatch1.getRoom()),
                ONLY_CHECK_XZ_COORDINATES,
                x -> false
        );
        Assertions.assertNull(site);
    }

    /**
     * Regression test: When a villager finishes work at a crafting table, they may step
     * outside the room boundaries while the job site block is at maxState (completed product).
     * The villager should still be considered "at" the job site for extraction purposes
     * if they're near the room's door (within 2 blocks).
     *
     * BUG: Without the fix, the villager gets NO_JOBSITE status and gives up,
     * never extracting the crafted item.
     */
    @Test
    void getEntityCurrentJobSite_shouldReturnRoom_WhenEntityNearDoorOfRoomWithCompletedProduct() {
        // Room with door at (0,0) and space from (-1,0) to (1,2)
        Room roomWithCompletedProduct = arbitaryRoomMatch1.getRoom();
        Position doorPos = roomWithCompletedProduct.getDoorPos(); // (0, 0)

        // Entity is 2 blocks away from the door (outside the room but nearby)
        Position entityNearDoor = new Position(doorPos.x + 2, doorPos.z);

        // Verify entity is NOT inside the room (precondition)
        Assertions.assertFalse(
                roomWithCompletedProduct.getSpaces().stream()
                        .anyMatch(space -> ca.bradj.roomrecipes.logic.InclusiveSpaces.contains(
                                ImmutableList.of(space), entityNearDoor)),
                "Test precondition: entity should NOT be inside the room"
        );

        EntityCurrentJobSite<Room> site = JobsClean.getEntityCurrentJobSite(
                entityNearDoor,
                new RoomsNeedingVillagerInput<>(ImmutableMap.of()),
                ImmutableList.of(roomWithCompletedProduct), // Room has completed product
                ONLY_CHECK_XZ_COORDINATES,
                x -> false
        );

        // With the fix: entity near door should be considered "at" the job site
        Assertions.assertNotNull(site,
                "Entity near door of room with completed product should be considered at job site for extraction");
        Assertions.assertEquals(roomWithCompletedProduct, site.room());
    }

    @Test
    void getEntityCurrentJobSite_shouldReturnCorrectRoom_WhenRoomWithRequirementsExists_AndNoResultsAvailable_IfEntityInRoom() {
        EntityCurrentJobSite<Room> site = JobsClean.getEntityCurrentJobSite(
                positionInsideArbitraryRoomMatch1,
                new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                        0,
                        ImmutableList.of(new RoomsNeedingVillagerInput.NVIRoom<>(arbitaryRoomMatch1, false))
                        // There is a room needing supplies at state 0
                )),
                ImmutableList.of(), // There are no finished results to grab
                ONLY_CHECK_XZ_COORDINATES,
                x -> false
        );
        Assertions.assertEquals(arbitaryRoomMatch1.getRoom(), site.room());
    }

    @Test
    void getEntityCurrentJobSite_shouldReturnNull_WhenRoomWithRequirementsExists_AndResultsAreNotAvailable_IfEntityInADifferentRoom() {
        EntityCurrentJobSite<Room> site = JobsClean.getEntityCurrentJobSite(
                positionInsideArbitraryRoomMatch1,
                new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                        0,
                        ImmutableList.of(new RoomsNeedingVillagerInput.NVIRoom<>(arbitaryRoomMatch2, false))
                        // There is a room needing supplies at state 0 (but the entity is in another room)
                )),
                ImmutableList.of(), // There are no finished results to grab
                ONLY_CHECK_XZ_COORDINATES,
                x -> false
        );
        Assertions.assertNull(site);
    }

    @Test
    void getSupplyItemStatuses_ShouldReturnCorrectResult_WhenSecondStateRequiresNothing_AndNoItemsHeld() {
        @NotNull ImmutableMap<Integer, SupplyItemStatus> sis = JobsClean.<TestItem>getSupplyItemStatuses(
                ImmutableList::of,
                ImmutableMap.of(
                        0, testItem -> "grapes".equals(testItem.value)
                        // Nothing required at 1
                ),
                ImmutableMap.of(
                        0, true,
                        1, false
                )::get,
                ImmutableMap.of(), // No tools required
                ImmutableMap.<Integer, Boolean>of()::get,
                ImmutableMap.of(), // No work required
                2
        );
        Assertions.assertEquals(SupplyItemStatus.NEEDS_ITEM, sis.get(0));
        Assertions.assertEquals(SupplyItemStatus.NOT_REQUIRED, sis.get(1));
    }

    @Test
    void getSupplyItemStatuses_ShouldReturnCorrectResult_WhenSecondStateRequiresNothing_AndNoItemsHeld_AddWork() {
        @NotNull ImmutableMap<Integer, SupplyItemStatus> sis = JobsClean.<TestItem>getSupplyItemStatuses(
                ImmutableList::of,
                ImmutableMap.of(
                        0, testItem -> "grapes".equals(testItem.value)
                        // Nothing required at 1
                ),
                ImmutableMap.of(
                        0, true,
                        1, false
                )::get,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, false,
                        1, false
                )::get,
                ImmutableMap.of(1, 1), // Work required at state 1
                2
        );
        Assertions.assertEquals(SupplyItemStatus.NEEDS_ITEM, sis.get(0));
        Assertions.assertEquals(SupplyItemStatus.NOT_REQUIRED, sis.get(1));
    }
}
