package ca.bradj.questown.town;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkStatusStoreTest {

    private static final Room arbitaryRoom = new Room(
            new Position(1, 2),
            new InclusiveSpace(new Position(3, 4), new Position(5, 6))
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
        s.setJobBlockStateWithTimer(pos, AbstractWorkStatusStore.State.fresh(), 10);
        @Nullable Integer tl = s.getTimeToNextState(pos);
        Assertions.assertEquals(10, tl);
    }

    @Test
    void Test_GetTimeLeftShouldReturnLessValueForSetBlockAfterTick() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, AbstractWorkStatusStore.State.fresh(), 10);
        tick(s);
        @Nullable Integer tl = s.getTimeToNextState(pos);
        Assertions.assertEquals(9, tl);
    }

    @Test
    void Test_GetTimeLeftShouldReturnNullForSetBlockAfterFinalTick() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, AbstractWorkStatusStore.State.fresh(), 1); // Only one tick left
        tick(s);
        @Nullable Integer tl = s.getTimeToNextState(pos);
        Assertions.assertNull(tl);
    }

    @Test
    void Test_ShouldMoveToNextStateAfterFinalTick() {
        TestWorkStatusStore s = new TestWorkStatusStore();
        Position pos = new Position(1, 2);
        s.setJobBlockStateWithTimer(pos, AbstractWorkStatusStore.State.fresh(), 1); // Only one tick left
        tick(s);
        AbstractWorkStatusStore.State state = s.getJobBlockState(pos);
        Assertions.assertNotNull(state);
        Assertions.assertEquals(1, state.processingState());
    }

}