package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

class AbstractWorkWITest {

    private static class TestWorkWI extends AbstractWorkWI<Position, Void, GathererJournalTest.TestItem, Void> {

        Map<Position, AbstractWorkStatusStore.State> state = new HashMap<>();

        public TestWorkWI(
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Integer> timeRequiredAtStates,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsRequiredAtStates
        ) {
            super(workRequiredAtStates, (x, s) -> timeRequiredAtStates.get(s), toolsRequiredAtStates);
        }

        @Override
        protected Void degradeTool(Void unused, @Nullable Void tuwn, Function<GathererJournalTest.TestItem, Boolean> testItemBooleanFunction) {
            return null;
        }

        @Override
        protected int getWorkSpeedOf10(Void unused) {
            return 10;
        }

        @Override
        protected ImmutableWorkStateContainer<Position, Void> getWorkStatuses(Void unused) {
            return new ImmutableWorkStateContainer<Position, Void>() {
                @Override
                public AbstractWorkStatusStore.@Nullable State getJobBlockState(Position bp) {
                    return state.get(bp);
                }

                @Override
                public ImmutableMap<Position, AbstractWorkStatusStore.State> getAll() {
                    return ImmutableMap.copyOf(state);
                }

                @Override
                public Void setJobBlockState(Position bp, AbstractWorkStatusStore.State bs) {
                    state.put(bp, bs);
                    return null;
                }

                @Override
                public Void setJobBlockStateWithTimer(Position bp, AbstractWorkStatusStore.State bs, int ticksToNextState) {
                    state.put(bp, bs);
                    throw new UnsupportedOperationException("Timers not supported by this test suite");
                }

                @Override
                public Void clearState(Position bp) {
                    state.clear();
                    return null;
                }

                @Override
                public boolean claimSpot(
                        Position bp,
                        Claim claim
                ) {
                    return true;
                }

                @Override
                public void clearClaim(Position position) {

                }

                @Override
                public boolean canClaim(
                        Position position,
                        Supplier<Claim> makeClaim
                ) {
                    return true;
                }
            };
        }
    }

    @Test
    void tryWork_shouldMoveToNextState_AfterWorkDone() {
        TestWorkWI wi = new TestWorkWI(
                ImmutableMap.of(
                        1, 2
                ),
                ImmutableMap.of(),
                ImmutableMap.of()
        );
        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 0, 1, new Position(0, 1)));
        Assertions.assertEquals(
                AbstractWorkStatusStore.State.freshAtState(1).setWorkLeft(2),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 1, 1, new Position(0, 1)));
        Assertions.assertEquals(
                AbstractWorkStatusStore.State.freshAtState(1).setWorkLeft(1),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 1, 1, new Position(0, 1)));
        Assertions.assertEquals(
                AbstractWorkStatusStore.State.freshAtState(1).setWorkLeft(0),
                wi.state.get(new Position(0, 0))
        );
    }

    @Test
    void tryWork_shouldMoveToNextState_AfterToolPresented() {
        TestWorkWI wi = new TestWorkWI(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(
                        1, (GathererJournalTest.TestItem t) -> true,
                        2, (GathererJournalTest.TestItem t) -> true
                )
        );
        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 0, 1, new Position(0, 1)));
        Assertions.assertEquals(
                AbstractWorkStatusStore.State.freshAtState(1),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 1, 1, new Position(0, 1)));
        Assertions.assertEquals(
                AbstractWorkStatusStore.State.freshAtState(2),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 2, 1, new Position(0, 1)));
        Assertions.assertEquals(
                AbstractWorkStatusStore.State.freshAtState(3),
                wi.state.get(new Position(0, 0))
        );
    }
}
