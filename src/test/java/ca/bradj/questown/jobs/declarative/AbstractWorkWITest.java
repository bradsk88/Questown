package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.jobs.WorkedSpot;
import ca.bradj.questown.logic.MonoPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static ca.bradj.questown.jobs.declarative.AbstractItemWITest.alwaysTrue;

class AbstractWorkWITest {

    private static class TestWorkWI extends AbstractWorkWI<Position, Void, GathererJournalTest.TestItem, Void> {

        Map<Position, State> state = new HashMap<>();

        public TestWorkWI(
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Integer> timeRequiredAtStates,
                ImmutableMap<Integer, MonoPredicateCollection<GathererJournalTest.TestItem>> toolsRequiredAtStates,
                BiConsumer<Void, WorkSpot<Integer, Position>> scCallback
        ) {
            super(workRequiredAtStates, (x, s) -> Util.getOrDefault(timeRequiredAtStates, s, 0), toolsRequiredAtStates, scCallback);
        }

        @Override
        protected Void degradeTool(Void unused, @Nullable Void tuwn, PredicateCollection<GathererJournalTest.TestItem, ?> itemBooleanFunction) {
            return null;
        }

        @Override
        protected Void setJobBlockStateWithTimer(Void unused, Position bp, State bs, int nextStepTime) {
            state.put(bp, bs);
            throw new UnsupportedOperationException("Timers not supported by this test suite");
        }

        @Override
        protected Void setJobBlockState(Void unused, Position bp, State bs) {
            state.put(bp, bs);
            return null;
        }

        @Override
        protected State getJobBlockState(Void unused, Position bp) {
            return state.get(bp);
        }

        @Override
        protected int getWorkSpeedOf10(Void unused) {
            return 10;
        }

    }

    @Test
    void tryWork_shouldMoveToNextState_AfterWorkDone() {
        TestWorkWI wi = new TestWorkWI(
                ImmutableMap.of(
                        1, 2
                ),
                ImmutableMap.of(),
                ImmutableMap.of(),
                (a, b) -> {}
        );
        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 0));
        Assertions.assertEquals(
                State.freshAtState(1).setWorkLeft(2),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 1));
        Assertions.assertEquals(
                State.freshAtState(1).setWorkLeft(1),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 1));
        Assertions.assertEquals(
                State.freshAtState(2).setWorkLeft(0),
                wi.state.get(new Position(0, 0))
        );
    }

    @Test
    void tryWork_shouldCallStateChangeListener_AfterWorkDone() {
        ArrayList<WorkSpot<Integer, Position>> calledBack = new ArrayList<>();
        TestWorkWI wi = new TestWorkWI(
                ImmutableMap.of(
                        1, 2
                ),
                ImmutableMap.of(),
                ImmutableMap.of(),
                (a, b) -> calledBack.add(b));
        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 0));
        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 1));
        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 1));
        Assertions.assertIterableEquals(ImmutableList.of(
                new WorkSpot<>(new Position(0, 0), 0, 0, new Position(0, 0)),
                new WorkSpot<>(new Position(0, 0), 1, 0, new Position(0, 0))
        ), calledBack);
    }

    @Test
    void tryWork_shouldNotCallStateChangeListener_IfFractionalWorkLeft() {
        ArrayList<WorkSpot<Integer, Position>> calledBack = new ArrayList<>();
        TestWorkWI wi = new TestWorkWI(
                ImmutableMap.of(
                        0, 1
                ),
                ImmutableMap.of(),
                ImmutableMap.of(),
                (a, b) -> calledBack.add(b)) {
            @Override
            protected int getWorkSpeedOf10(Void unused) {
                return 5; // Makes work degrade in steps less than integer
            }
        };
        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 0));
        Assertions.assertEquals(0, calledBack.size());
    }

    @Test
    void tryWork_shouldMoveToNextState_AfterToolPresented() {
        TestWorkWI wi = new TestWorkWI(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(
                        1, alwaysTrue,
                        2, alwaysTrue
                ),
                (a, b) -> {});
        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 0));
        Assertions.assertEquals(
                State.freshAtState(1),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 1));
        Assertions.assertEquals(
                State.freshAtState(2),
                wi.state.get(new Position(0, 0))
        );

        wi.tryWork(null, new WorkedSpot<>(new Position(0, 0), 2));
        Assertions.assertEquals(
                State.freshAtState(3),
                wi.state.get(new Position(0, 0))
        );
    }
}
