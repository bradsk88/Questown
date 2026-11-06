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

/** A work-status store seeds the welcome mat only on a tick that includes the mat's room. */
class WorkStatusStoreMatSeedTest {

    private static final Position MAT = new Position(1, 1);
    private static final Room MAT_ROOM = new Room(new Position(2, 2), InclusiveSpace.from(0, 0).to(4, 4));
    private static final Room OTHER_ROOM = new Room(new Position(-2, -2), InclusiveSpace.from(-6, -6).to(-1, -1));

    static class MatStore extends AbstractWorkStatusStore<Position, String, Room, Object> {
        MatStore() {
            super(
                    (room, pos) -> ImmutableList.of(pos),
                    (ts, pos) -> false,
                    (ts, pos) -> MAT.equals(pos) ? State.fresh() : null,
                    (ts, pos) -> null
            );
        }
    }

    void tickWith(MatStore store, List<Room> rooms) {
        store.tick(new Object(), rooms, 1L);
    }

    @Test
    void storeNeverTickedOverTheGateRoom_neverSeedsTheWelcomeMat() {
        MatStore store = new MatStore();
        tickWith(store, List.of(OTHER_ROOM));
        tickWith(store, List.of(OTHER_ROOM));
        tickWith(store, List.of(OTHER_ROOM));
        Assertions.assertNull(store.getJobBlockState(MAT));
    }

    @Test
    void tickOverTheGateRoom_seedsTheWelcomeMatAtState0() {
        MatStore store = new MatStore();
        tickWith(store, List.of());
        Assertions.assertNull(store.getJobBlockState(MAT));
        tickWith(store, List.of(OTHER_ROOM, MAT_ROOM));
        Assertions.assertEquals(State.fresh(), store.getJobBlockState(MAT));
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
    void unseededWelcomeMat_readsAsWorkableAtState0_soTheLeaverCanStart() {
        MatStore store = new MatStore();
        Assertions.assertNull(store.getJobBlockState(MAT));

        var workable = DeclarativeJobs.rooms(
                () -> ImmutableList.copyOf(matNvi().get().get(0)),
                store::getJobBlockState,
                pos -> true,
                pos -> pos.toString(),
                3
        );
        Assertions.assertTrue(workable.get(0).populate().value().value());
    }
}
