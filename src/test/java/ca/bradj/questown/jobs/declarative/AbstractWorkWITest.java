package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

class AbstractWorkWITest {

    private static class TestWorkWI extends AbstractWorkWI<Position, Void, GathererJournalTest.TestItem, Void> {

        Map<Position, AbstractWorkStatusStore.State> state = new HashMap<>();

        public TestWorkWI(
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Integer> timeRequiredAtStates,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsRequiredAtStates
        ) {
            super(workRequiredAtStates, timeRequiredAtStates, toolsRequiredAtStates);
        }

        @Override
        protected Void degradeTool(Void unused, @Nullable Void tuwn, Function<GathererJournalTest.TestItem, Boolean> testItemBooleanFunction) {
            return null;
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
        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 0, 1));
        Assertions.assertEquals(
                new AbstractWorkStatusStore.State(1, 0, 2),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 1, 1));
        Assertions.assertEquals(
                new AbstractWorkStatusStore.State(1, 0, 1),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 1, 1));
        Assertions.assertEquals(
                new AbstractWorkStatusStore.State(2, 0, 0),
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
        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 0, 1));
        Assertions.assertEquals(
                new AbstractWorkStatusStore.State(1, 0, 0),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 1, 1));
        Assertions.assertEquals(
                new AbstractWorkStatusStore.State(2, 0, 0),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkSpot<>(new Position(0, 0), 2, 1));
        Assertions.assertEquals(
                new AbstractWorkStatusStore.State(3, 0, 0),
                wi.state.get(new Position(0, 0))
        );
    }
}
