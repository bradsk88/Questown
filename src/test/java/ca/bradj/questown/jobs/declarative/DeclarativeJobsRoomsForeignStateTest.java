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

/**
 * 2026-10-14 playtest (server-19): BOP deposit soft-lock. The flag block is a shared
 * work location for works with different state numberings - bop_depositing (states 0-2)
 * and downtime (states 0-9). When the gatherer's downtime cycle was interrupted
 * (downtime → seeking_work mid-sequence), the flag's per-owner state record was left at
 * 3. The status gate ({@code DeclarativeJobs.rooms}) read the raw 3, found no spot at
 * state 0 or 1, and the depositor looped in NO_JOBSITE forever - and each re-entry
 * re-granted a BOP ("duplicating BOPs", user chat 04:17:58/04:18:18).
 *
 * <p>An out-of-range record (state &gt; this job's maxState) can never advance itself in
 * this job's state machine; it must be read as fresh - the same rule the supply-loop
 * guard already applies ({@code DeclarativeJobTicker.computeRoomsNeedingInput}).
 */
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
        // A foreign work's (downtime's, max 9) mid-sequence record: 3.
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
        // An in-range record must NOT be read as fresh: a state-1 spot is workable for
        // the state-1 gate only. Guards against over-normalizing every record to fresh.
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
        // Pre-existing contract (WorkStatusStoreMatSeedTest lineage): an unseeded block
        // is unstarted work, not "no state".
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
}
