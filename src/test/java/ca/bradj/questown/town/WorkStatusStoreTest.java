package ca.bradj.questown.town;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkStatusStoreTest {

    private static final Room arbitaryRoom = new Room(
            new Position(1, 2),
            InclusiveSpace.from(3, 4).to(5, 6)
    );

    private static void tick(TestWorkStatusStore s) {
        // Arguments don't matter for timing logic, rooms just can't be empty due to optimizations
        s.tick(null, ImmutableList.of(arbitaryRoom), 1);
    }

    private static void tick(TestWorkStatusStore s, int interval) {
        s.tick(null, ImmutableList.of(arbitaryRoom), interval);
    }

    private static class TestWorkStatusStore extends AbstractWorkStatusStore<Position, TestItem, Room, Void> {

        private final @Nullable Position restingPos;

        public TestWorkStatusStore() {
            this(null);
        }

        public TestWorkStatusStore(@Nullable Position restingPos) {
            super(
                    (room, pos) -> ImmutableList.of(pos),
                    (lvl, pos) -> false,
                    (lvl, pos) -> null,
                    (lvl, pos) -> null
            );
            this.restingPos = restingPos;
        }

        @Override
        protected boolean isRestingState(
                Void tickSource,
                Position pos,
                State state
        ) {
            return pos.equals(restingPos);
        }

    }

    /** A job that claims no spots can work a block someone else claimed. */
    @Test
    void canClaim_aJobWithNoClaimMayWorkAClaimedSpot() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.claimSpot(pos, new Claim(UUID.randomUUID(), 100));

        Assertions.assertTrue(s.canClaim(pos, () -> null));
    }

    @Test
    void Test_GetTimeLeftShouldReturnNullForUntouchedBlock() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        @Nullable Integer tl = s.getTimeToNextState(new Position(1, 2)); // Arbitrary, never touched
        Assertions.assertNull(tl);
    }

    @Test
    void Test_GetTimeLeftShouldReturnValueForSetBlock() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, State.fresh(), 10);
        @Nullable Integer tl = s.getTimeToNextState(pos);
        Assertions.assertEquals(10, tl);
    }

    @Test
    void Test_GetTimeLeftShouldReturnLessValueForSetBlockAfterTick() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, State.fresh(), 10);
        tick(s);
        @Nullable Integer tl = s.getTimeToNextState(pos);
        Assertions.assertEquals(9, tl);
    }

    @Test
    void Test_GetTimeLeftShouldReturnNullForSetBlockAfterFinalTick() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, State.fresh(), 1); // Only one tick left
        tick(s);
        @Nullable Integer tl = s.getTimeToNextState(pos);
        Assertions.assertNull(tl);
    }

    @Test
    void Test_TimerShouldDecayOncePerTickRegardlessOfHowManyRoomsAreNew() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, State.fresh(), 10);

        // Five rooms in one tick: the timer still drops by `ticksSinceLast`.
        s.tick(null, roomsAt(0, 10, 20, 30, 40), 1);

        Assertions.assertEquals(9, s.getTimeToNextState(pos));
    }

    private static ImmutableList<Room> roomsAt(int... xs) {
        ImmutableList.Builder<Room> b = ImmutableList.builder();
        for (int x : xs) {
            b.add(new Room(new Position(x, 2), InclusiveSpace.from(x, 4).to(x + 2, 6)));
        }
        return b.build();
    }

    @Test
    void Test_ShouldMoveToNextStateAfterFinalTick() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, State.fresh(), 1); // Only one tick left
        tick(s);
        State state = s.getJobBlockState(pos);
        Assertions.assertNotNull(state);
        Assertions.assertEquals(1, state.processingState());
    }

    @Test
    void Test_ClaimExpiryReleasesTheSpot() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        UUID other = UUID.randomUUID();
        s.claimSpot(pos, new Claim(UUID.randomUUID(), 3));
        tick(s);
        tick(s);
        Assertions.assertFalse(s.canClaim(pos, () -> new Claim(other, 100)));
        tick(s);
        Assertions.assertTrue(s.canClaim(pos, () -> new Claim(other, 100)));
    }

    @Test
    void Test_ClaimTtlIsMeasuredInGameTicksNotFlagTicks() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        UUID other = UUID.randomUUID();
        s.claimSpot(pos, new Claim(UUID.randomUUID(), 10));
        s.tick(null, ImmutableList.of(arbitaryRoom), 5);
        Assertions.assertFalse(s.canClaim(pos, () -> new Claim(other, 100)));
        s.tick(null, ImmutableList.of(arbitaryRoom), 5);
        Assertions.assertTrue(s.canClaim(pos, () -> new Claim(other, 100)));
    }

    @Test
    void Test_ReclaimingByTheSameOwnerRefreshesTheTtl() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        s.claimSpot(pos, new Claim(owner, 3));
        tick(s);
        // The same owner re-claiming renews it.
        Assertions.assertTrue(s.claimSpot(pos, new Claim(owner, 3)));
        tick(s);
        Assertions.assertFalse(s.canClaim(pos, () -> new Claim(other, 100)));
    }

    @Test
    void Test_StuckBlockWithNoTimerReseedsToFreshAfterThreshold() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockState(pos, State.freshAtState(1));
        tick(s, (int) AbstractWorkStatusStore.STUCK_RESET_TICKS - 1);
        Assertions.assertEquals(1, s.getJobBlockState(pos).processingState());
        tick(s, 1);
        Assertions.assertEquals(0, s.getJobBlockState(pos).processingState());
    }

    @Test
    void Test_RestingStateBlockIsNotReseeded() {
        Position board = new Position(1, 2);
        TestWorkStatusStore s = new TestWorkStatusStore(board);
        s.setJobBlockState(board, State.freshAtState(1));
        tick(s, (int) AbstractWorkStatusStore.STUCK_RESET_TICKS * 3);
        Assertions.assertEquals(1, s.getJobBlockState(board).processingState());
    }

    @Test
    void Test_NonRestingBlockIsStillReseededBesideARestingOne() {
        Position board = new Position(1, 2);
        Position mat = new Position(3, 4);
        TestWorkStatusStore s = new TestWorkStatusStore(board);
        s.setJobBlockState(board, State.freshAtState(1));
        s.setJobBlockState(mat, State.freshAtState(3));
        tick(s, (int) AbstractWorkStatusStore.STUCK_RESET_TICKS);
        Assertions.assertEquals(1, s.getJobBlockState(board).processingState());
        Assertions.assertEquals(0, s.getJobBlockState(mat).processingState());
    }

    @Test
    void Test_StuckBlockCounterResetsOnStateWrite() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockState(pos, State.freshAtState(1));
        int almostStuck = (int) AbstractWorkStatusStore.STUCK_RESET_TICKS - 1000;
        tick(s, almostStuck);
        s.setJobBlockState(pos, State.freshAtState(1).setCount(2));
        tick(s, almostStuck);
        Assertions.assertEquals(1, s.getJobBlockState(pos).processingState());
    }

    @Test
    void Test_BlockWithActiveTimerIsNotReseeded() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        int stuckTicks = (int) AbstractWorkStatusStore.STUCK_RESET_TICKS;
        s.setJobBlockStateWithTimer(pos, State.freshAtState(1), stuckTicks + 10_000);
        tick(s, stuckTicks);
        Assertions.assertEquals(1, s.getJobBlockState(pos).processingState());
        Assertions.assertNotNull(s.getTimeToNextState(pos));
    }

    @Test
    void Test_FreshStateBlockIsNeverReseeded() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockState(pos, State.fresh());
        tick(s, 100_000);
        Assertions.assertEquals(0, s.getJobBlockState(pos).processingState());
        Assertions.assertEquals(State.fresh(), s.getJobBlockState(pos));
    }

}