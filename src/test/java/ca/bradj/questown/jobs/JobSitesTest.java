package ca.bradj.questown.jobs;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;

class JobSitesTest {

    @Test
    public void returnNullIfNoMatches() {
        Position result = JobSites.find(
                ImmutableList::of,
                (match) -> ImmutableList.of(),
                match -> null,
                pos -> AbstractWorkStatusStore.State.fresh(),
                ImmutableMap.of(),
                2,
                ImmutableList.of(),
                new TestPosKit()
        );
        Assertions.assertNull(result);
    }
    @Test
    public void returnNullIfNoStates() {
        ImmutableList<TestRoomMatch> matches = ImmutableList.of(
                new TestRoomMatch("shop")
        );

        Position result = JobSites.<Position, TestRoomMatch, Room>find(
                () -> matches,
                (match) -> ImmutableList.of(
                        new AbstractMap.SimpleEntry<>(new Position(0, 0), "")
                ),
                match -> null,
                pos -> null, // no states
                ImmutableMap.of(),
                2,
                ImmutableList.of(),
                new TestPosKit()
        );
        Assertions.assertNull(result);
    }
    @Test
    public void returnPositionInFrontOfBlockIfNonMaxStateAndVillagerHasItem() {
        ImmutableList<TestRoomMatch> matches = ImmutableList.of(
                new TestRoomMatch("shop")
        );

        ImmutableMap<Integer, Boolean> statusItems = ImmutableMap.of(
                0, false,
                1, true, // Has the items for state 1
                2, false
        );

        ImmutableMap<TestRoomMatch, Room> rooms = ImmutableMap.of(
                matches.get(0), new Room(
                new Position(0, 2),
                new InclusiveSpace(
                        new Position(-1, -1),
                        new Position(1, 2)
                )
        ));

        Position result = JobSites.find(
                () -> matches,
                (match) -> ImmutableList.of(
                        new AbstractMap.SimpleEntry<>(new Position(0, 0), "")
                ),
                rooms::get,
                pos -> AbstractWorkStatusStore.State.freshAtState(1), // not max
                statusItems,
                2,
                ImmutableList.of(),
                new TestPosKit()
        );
        Assertions.assertEquals(new Position(0, 1), result);
    }
    @Test
    public void returnPosIfMaxState() {

        int notMaxState = 1;
        int maxState = 2;

        ImmutableList<TestRoomMatch> matches = ImmutableList.of(
                new TestRoomMatch("kitchen"),
                new TestRoomMatch("bedroom"),
                new TestRoomMatch("factory")
        );

        ImmutableMap<TestRoomMatch, Collection<Map.Entry<Position, ?>>> containedBlocks = ImmutableMap.of(
                matches.get(0), ImmutableList.of(new AbstractMap.SimpleEntry<>(
                        new Position(0, 0), ""
                )),
                matches.get(1), ImmutableList.of(new AbstractMap.SimpleEntry<>(
                        new Position(1, 1), ""
                )),
                matches.get(2), ImmutableList.of(new AbstractMap.SimpleEntry<>(
                        new Position(2, 2), ""
                ))
        );

        ImmutableMap<Position, AbstractWorkStatusStore.State> states = ImmutableMap.of(
                new Position(0, 0),
                AbstractWorkStatusStore.State.freshAtState(notMaxState),
                new Position(1, 1),
                AbstractWorkStatusStore.State.freshAtState(maxState),
                new Position(2, 2),
                AbstractWorkStatusStore.State.freshAtState(notMaxState)
        );

        Position result = JobSites.find(
                () -> matches,
                containedBlocks::get,
                match -> null,
                states::get,
                ImmutableMap.of(),
                2,
                ImmutableList.of(),
                new TestPosKit()
        );
        Assertions.assertEquals(new Position(1, 1), result);
    }

    @Test
    void getDoorDirectionFromCenter_DoorNorth() {
        Position jobBlock = new Position(2, 2);
        Position doorBlock = new Position(2, 0);
        InclusiveSpace space = new InclusiveSpace(
                new Position(0, 0),
                new Position(4, 4)
        );
        Position doorSidePos = JobSites.getDoorSideAdjacentPosition(
                new Room(doorBlock, space),
                new TestPosKit(),
                jobBlock
        );
        Assertions.assertEquals(
                new Position(2, 1),
                doorSidePos
        );

    }
    @Test
    void getDoorDirectionFromCenter_DoorSouth() {
        Position jobBlock = new Position(2, 2);
        Position doorBlock = new Position(2, 4);
        InclusiveSpace space = new InclusiveSpace(
                new Position(0, 0),
                new Position(4, 4)
        );
        Position doorSidePos = JobSites.getDoorSideAdjacentPosition(
                new Room(doorBlock, space),
                new TestPosKit(),
                jobBlock
        );
        Assertions.assertEquals(
                new Position(2, 3),
                doorSidePos
        );
    }
    @Test
    void getDoorDirectionFromCenter_DoorWest() {
        Position jobBlock = new Position(2, 2);
        Position doorBlock = new Position(0, 2);
        InclusiveSpace space = new InclusiveSpace(
                new Position(0, 0),
                new Position(4, 4)
        );
        Position doorSidePos = JobSites.getDoorSideAdjacentPosition(
                new Room(doorBlock, space),
                new TestPosKit(),
                jobBlock
        );
        Assertions.assertEquals(
                new Position(1, 2),
                doorSidePos
        );
    }
    @Test
    void getDoorDirectionFromCenter_DoorEast() {
        Position jobBlock = new Position(2, 2);
        Position doorBlock = new Position(4, 2);
        InclusiveSpace space = new InclusiveSpace(
                new Position(0, 0),
                new Position(4, 4)
        );
        Position doorSidePos = JobSites.getDoorSideAdjacentPosition(
                new Room(doorBlock, space),
                new TestPosKit(),
                jobBlock
        );
        Assertions.assertEquals(
                new Position(3, 2),
                doorSidePos
        );
    }
}