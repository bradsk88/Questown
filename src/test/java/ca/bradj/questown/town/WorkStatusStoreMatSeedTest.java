package ca.bradj.questown.town;

import ca.bradj.questown.jobs.JobsClean;
import ca.bradj.questown.jobs.declarative.TestRoomMatch;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Leaver soft-lock (village/self_sufficiency, NO_JOBSITE / COLLECTING_SUPPLIES at the gate mat):
 * a leaver's per-owner work-status store is bootstrapped exactly once at creation
 * (TownFlagBlockEntity#getRealWorkStatusHandle). If the gate mat's room is not in the
 * room list at that moment — or the level is unavailable — the mat is never seeded into
 * that store and nothing re-offers it. These tests pin the store-level consequence in the
 * real code (no simulated store; the test subclass only supplies the generic type parameters).
 *
 * Evidence this encodes (2026-10-08, worlds 806/809/810): one PROBE:matInit world-wide
 * (a single owner store ever seeded the mat), the shared jobHandle keeping matInScannable
 * scans while stalled owner stores did not, and the same mat reading p0 for the shared
 * store while the owner's read path saw no workable spot.
 */
class WorkStatusStoreMatSeedTest {

    private static final Position MAT = new Position(1, 1);
    private static final Room MAT_ROOM = new Room(new Position(2, 2), InclusiveSpace.from(0, 0).to(4, 4));
    private static final Room OTHER_ROOM = new Room(new Position(-2, -2), InclusiveSpace.from(-6, -6).to(-1, -1));

    /** Real store; the subclass only names the generic parameters (mirrors TestWorkStatusHandle). */
    static class MatStore extends AbstractWorkStatusStore<Position, String, Room, Object> {
        MatStore() {
            super(
                    (room, pos) -> ImmutableList.of(pos),
                    (ts, pos) -> false,
                    (ts, pos) -> MAT.equals(pos) ? State.welcomeMatSeedState() : null,
                    (ts, pos) -> null
            );
        }
    }

    void tickWith(MatStore store, List<Room> rooms) {
        store.tick(new Object(), rooms, 1L);
    }

    @Test
    void storeThatNeverSeesGateRoom_inRoomList_neverSeedsMat() {
        MatStore store = new MatStore();
        // Bootstrap (one pass, store-creation tick) with a room list lacking the gate room
        tickWith(store, List.of(OTHER_ROOM));
        // Subsequent upkeep ticks, same (gate-room-less) room list: the mat is never offered.
        tickWith(store, List.of(OTHER_ROOM));
        tickWith(store, List.of(OTHER_ROOM));
        Assertions.assertNull(store.getJobBlockState(MAT),
                "a gate mat outside every scanned room must not exist in the store (this is the per-owner 'mat missing' state)");

    }

    @Test
    void storeThatIsTicked_withGateRoom_inRoomList_doesSeedTheMat() {
        MatStore store = new MatStore();
        // Bootstrap that ran with an empty room list (store created before the gate room existed)
        tickWith(store, List.of());
        Assertions.assertNull(store.getJobBlockState(MAT));
        // The moment a tick carries the gate room, seeding happens — the store CAN heal,
        // so the defect is that the owner store is such a tick, not the store itself.
        tickWith(store, List.of(OTHER_ROOM, MAT_ROOM));
        Assertions.assertEquals(State.welcomeMatSeedState(), store.getJobBlockState(MAT),
                "a tick over the gate room seeds the mat (real store behavior)");
        Assertions.assertEquals(0, store.getJobBlockState(MAT).processingState(),
                "the seeded state is state 0, the 'needs the leaver's supplies' state");
    }

    private RoomsNeedingVillagerInput<Room, String, Position> matNvi() {
        TestRoomMatch match = new TestRoomMatch(
                MAT_ROOM,
                ImmutableList.of("questown:special_quest.town_gate"),
                ImmutableMap.of(MAT, "questown:welcome_mat_block")
        );
        return new RoomsNeedingVillagerInput<>(ImmutableMap.of(
                0, List.of(new RoomsNeedingVillagerInput.NVIRoom<>(match, false))
        ));
    }

    @Test
    void jobsCleanRooms_readPath_treatsMatMissingState_asWorkableSpot_afterFix() {
        MatStore store = new MatStore();
        Assertions.assertNull(store.getJobBlockState(MAT), "precondition: the owner store lacks the mat");

        // Before the fix this gate read null as "no state" and the leaver got NO_JOBSITE here;
        // the corrected invariant (matching findJobSpot and the sibling null->fresh sites) is
        // that an unseeded mat = unstarted work = state 0, i.e. workable. This test is red
        // without the JobsClean.rooms fix and green with it.
        var workable = JobsClean.rooms(
                () -> com.google.common.collect.ImmutableList.copyOf(matNvi().get().get(0)),
                store::getJobBlockState,
                pos -> true,
                pos -> pos.toString(),
                3
        );
        Assertions.assertTrue(workable.get(0).populate().value().value(),
                "a leaver's unseeded mat must read as workable at state 0 (NO_JOBSITE stall fixed)");
    }

    @Test
    void jobsCleanRooms_readPath_withNullAsFresh_supplier_seesWorkableSpot() {
        MatStore store = new MatStore();
        Assertions.assertNull(store.getJobBlockState(MAT), "precondition: the owner store lacks the mat");

        // The null->fresh supplier (option A, cf. bsFn at DeclarativeJobTicker:203 and
        // the rni guard at :279): the identical store + mat room now yields a workable spot.
        var workable = JobsClean.rooms(
                () -> com.google.common.collect.ImmutableList.copyOf(matNvi().get().get(0)),
                pos -> {
                    State s = store.getJobBlockState(pos);
                    return s == null ? State.fresh() : s;
                },
                pos -> true,
                pos -> pos.toString(),
                3
        );
        Assertions.assertTrue(workable.get(0).populate().value().value(),
                "null-treated-as-fresh must make the mat workable (validates the read-side fix)");
    }

    @Test
    void todo_ownerStoreOneShotBootstrap_isNotReachableInAUnitTest() {
        // TownFlagBlockEntity#getRealWorkStatusHandle bootstraps the owner store with exactly
        // one tick (and skips it when the level is null). Making that bootstrap self-healing
        // (option B) lives on a BlockEntity bound to a live ServerLevel and cannot be driven
        // from a plain unit test; the store-level consequences it creates are covered by the
        // other tests in this class.
        Assertions.fail("not unit-testable: needs a Forge ServerLevel (see class doc)");
    }
}
