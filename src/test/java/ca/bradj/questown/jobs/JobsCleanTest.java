package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

class JobsCleanTest {

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
        public String getRecipeID() {
            return "test 1";
        }

        @Override
        public Room getRoom() {
            return new Room(new Position(0, 0), InclusiveSpaces.from(-1, 0).to(1, 2));
        }

        @Override
        public ImmutableMap<Position, String> getContainedBlocks() {
            return ImmutableMap.of(
                    new Position(0, 1), "chest"
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
}
