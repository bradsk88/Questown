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

    private static class TestWorkStatusStore extends AbstractWorkStatusStore<Position, TestItem, Room, Void> {

        public TestWorkStatusStore() {
            super(
                    (room, pos) -> ImmutableList.of(pos),
                    (lvl, pos) -> false,
                    (lvl, pos) -> null,
                    (lvl, pos) -> null
            );
        }

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

        // Five rooms appear in the same tick. The timer must still only lose `ticksSinceLast`.
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
    void Test_ClaimHoldsTheSpotUntilItsTtlRunsOut() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        UUID other = UUID.randomUUID();
        s.claimSpot(pos, new Claim(UUID.randomUUID(), 3));
        tick(s); // 3 -> 2
        tick(s); // 2 -> 1
        // Still held while ticks remain: another owner cannot claim the spot.
        Assertions.assertFalse(s.canClaim(pos, () -> new Claim(other, 100)));
    }

    @Test
    void Test_ClaimExpiryReleasesTheSpot() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        UUID other = UUID.randomUUID();
        s.claimSpot(pos, new Claim(UUID.randomUUID(), 3));
        tick(s); // 3 -> 2
        tick(s); // 2 -> 1
        Assertions.assertFalse(s.canClaim(pos, () -> new Claim(other, 100)));
        tick(s); // 1 -> 0, expired and released
        Assertions.assertTrue(s.canClaim(pos, () -> new Claim(other, 100)));
    }

    @Test
    void Test_ClaimTtlIsMeasuredInGameTicksNotFlagTicks() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        UUID other = UUID.randomUUID();
        s.claimSpot(pos, new Claim(UUID.randomUUID(), 10));
        // A flag tick advances by the game-tick delta (5), not by 1.
        s.tick(null, ImmutableList.of(arbitaryRoom), 5); // 10 -> 5
        Assertions.assertFalse(s.canClaim(pos, () -> new Claim(other, 100)));
        s.tick(null, ImmutableList.of(arbitaryRoom), 5); // 5 -> 0, expired
        Assertions.assertTrue(s.canClaim(pos, () -> new Claim(other, 100)));
    }

    @Test
    void Test_ReclaimingByTheSameOwnerRefreshesTheTtl() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        s.claimSpot(pos, new Claim(owner, 3));
        tick(s); // 3 -> 2
        // Re-claiming by the same owner refreshes the TTL (an active townie re-claims each insert).
        Assertions.assertTrue(s.claimSpot(pos, new Claim(owner, 3)));
        tick(s); // would have reached 1; the refresh put it back to 3 -> 2
        Assertions.assertFalse(s.canClaim(pos, () -> new Claim(other, 100)));
    }

}