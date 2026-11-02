package ca.bradj.questown.town;

import ca.bradj.questown.jobs.declarative.DeclarativeJobs;

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

/** A villager's work-status store seeds the welcome mat only on a tick whose room list includes the mat's room. */
class WorkStatusStoreMatSeedTest {

    private static final Position MAT = new Position(1, 1);
    private static final Room MAT_ROOM = new Room(new Position(2, 2), InclusiveSpace.from(0, 0).to(4, 4));
    private static final Room OTHER_ROOM = new Room(new Position(-2, -2), InclusiveSpace.from(-6, -6).to(-1, -1));

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
        tickWith(store, List.of(OTHER_ROOM));
        tickWith(store, List.of(OTHER_ROOM));
        tickWith(store, List.of(OTHER_ROOM));
        Assertions.assertNull(store.getJobBlockState(MAT),
                "a gate mat outside every scanned room must not exist in the store (this is the per-owner 'mat missing' state)");

    }

    @Test
    void storeThatIsTicked_withGateRoom_inRoomList_doesSeedTheMat() {
        MatStore store = new MatStore();
        tickWith(store, List.of());
        Assertions.assertNull(store.getJobBlockState(MAT));
        // Once a tick includes the mat's room, it seeds.
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

        // An unseeded mat reads as fresh state 0, workable.
        var workable = DeclarativeJobs.rooms(
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

        var workable = DeclarativeJobs.rooms(
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

}
