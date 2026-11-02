package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Jobs that share the flag number their states differently, so a record beyond this job's last state reads as fresh. */
class DeclarativeJobsRoomsForeignStateTest {

    private static final Position FLAG = new Position(0, 0);

    private static Room flagRoom() {
        return new Room(FLAG, InclusiveSpace.from(0, 0).to(0, 0));
    }

    private static ImmutableList<RoomsNeedingVillagerInput.NVIRoom<Room, String, Position>> flagSites() {
        return ImmutableList.of(new RoomsNeedingVillagerInput.NVIRoom<>(
                new TestRoomMatch(
                        flagRoom(),
                        ImmutableList.of("town_flag"),
                        ImmutableMap.of(FLAG, "flag")
                ),
                false
        ));
    }

    @Test
    void rooms_foreignStateBeyondMaxStateReadsAsFresh() {
        // Downtime's record, at 3.
        var gates = DeclarativeJobs.rooms(
                DeclarativeJobsRoomsForeignStateTest::flagSites,
                pos -> FLAG.equals(pos) ? State.freshAtState(3) : null,
                pos -> true,
                pos -> pos.toString(),
                2 // bop_depositing's max state
        );
        WithReason<Boolean> gate0 = gates.get(0).apply(() -> null);
        assertTrue(gate0.value,
                "a state-3 record is out of range for a max-2 job and must read as fresh; gate said: " + gate0.reason);
    }

    @Test
    void rooms_inRangeRecordStillGatesOnItsOwnState() {
        // An in-range record is not read as fresh.
        var gates = DeclarativeJobs.rooms(
                DeclarativeJobsRoomsForeignStateTest::flagSites,
                pos -> FLAG.equals(pos) ? State.freshAtState(1) : null,
                pos -> true,
                pos -> pos.toString(),
                2
        );
        assertFalse(gates.get(0).apply(() -> null).value,
                "a state-1 record must not satisfy the state-0 gate");
        assertTrue(gates.get(1).apply(() -> null).value,
                "a state-1 record must satisfy the state-1 gate");
    }

    @Test
    void rooms_nullRecordStillReadsAsFresh() {
        // A block with no record is fresh.
        var gates = DeclarativeJobs.rooms(
                DeclarativeJobsRoomsForeignStateTest::flagSites,
                pos -> null,
                pos -> true,
                pos -> pos.toString(),
                2
        );
        assertTrue(gates.get(0).apply(() -> null).value,
                "an unseeded block must read as state 0");
    }

    /** Downtime's record at the flag doesn't put the flag under a state the depositor never works. */
    @Test
    void inRange_foreignStateBeyondMaxStateReadsAsFresh() {
        var read = DeclarativeJobs.inRange(pos -> State.freshAtState(8), 2);
        assertTrue(read.apply(FLAG).processingState() == 0, "a state-8 record is out of range for a max-2 job");
    }

    @Test
    void inRange_keepsInRangeAndMissingRecords() {
        assertTrue(DeclarativeJobs.inRange(pos -> State.freshAtState(1), 2).apply(FLAG).processingState() == 1);
        assertTrue(DeclarativeJobs.<Position>inRange(pos -> null, 2).apply(FLAG) == null,
                "a missing record means the spot is not seeded, which is not the same as fresh");
    }
}
