package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** A block in two overlapping job sites appears once in the per-spot maps. */
class DeclarativeJobsRoomsSharedBlockTest {

    private static final Position SHARED = new Position(1, 1);

    private static RoomsNeedingVillagerInput.NVIRoom<Room, String, Position> site(Position door) {
        return new RoomsNeedingVillagerInput.NVIRoom<>(
                new TestRoomMatch(
                        new Room(door, InclusiveSpace.from(0, 0).to(2, 2)),
                        ImmutableList.of("plate"),
                        ImmutableMap.of(SHARED, "plate")
                ),
                false
        );
    }

    private static ImmutableList<RoomsNeedingVillagerInput.NVIRoom<Room, String, Position>> overlappingSites() {
        return ImmutableList.of(site(new Position(0, 0)), site(new Position(2, 2)));
    }

    @Test
    void rooms_blockInTwoJobSites_doesNotCrashAndStaysWorkable() {
        var gates = DeclarativeJobs.rooms(
                DeclarativeJobsRoomsSharedBlockTest::overlappingSites,
                pos -> State.freshAtState(0),
                pos -> true,
                pos -> pos.toString(),
                2
        );
        WithReason<Boolean> gate0 = gates.get(0).apply(() -> null);
        assertTrue(gate0.value, "the shared state-0 block must be workable; gate said: " + gate0.reason);
    }
}
