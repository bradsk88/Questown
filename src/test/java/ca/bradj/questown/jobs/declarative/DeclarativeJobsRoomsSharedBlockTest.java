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

/**
 * 2026-10-23 playtest r12 #11: the server crashed with "Multiple entries with same key"
 * while a diner evaluated its rooms gate in a town with a Kitchen Diner. The gate's
 * per-spot maps were built across every job-site match, so a block contained by two
 * matches (overlapping job sites) was put twice into an {@code ImmutableMap.Builder}.
 * A spot's state and job-block flag depend only on its position, so one entry per
 * position is the whole truth.
 */
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
