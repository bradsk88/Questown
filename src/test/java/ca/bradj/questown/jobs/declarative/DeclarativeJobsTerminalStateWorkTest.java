package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A meal in the diner's final state is a product to extract, not unfinished work. */
class DeclarativeJobsTerminalStateWorkTest {

    private static final int DINER_MAX_STATE = 2;

    private static final int DINER_CONSUME_FOOD = 1;

    private static Collection<Integer> unfinished(State flagSpot) {
        Collection<Supplier<Collection<String>>> rooms = ImmutableList.of(() -> ImmutableList.of("flag"));
        return DeclarativeJobs.getStatesWithUnfinishedWork(
                rooms,
                pos -> flagSpot,
                pos -> true,
                state -> DeclarativeJobs.declaresUnfinishedWork(
                        state, DINER_MAX_STATE, true, state == DINER_CONSUME_FOOD
                )
        );
    }

    @Test
    void mealReady_atTerminalState_isNotUnfinishedWork() {
        assertEquals(ImmutableList.of(), unfinished(State.freshAtState(DINER_MAX_STATE)));
    }

    @Test
    void mealInProgress_belowTerminal_isStillUnfinishedWork() {
        assertEquals(ImmutableList.of(0), unfinished(State.freshAtState(0)));
    }

    @Test
    void mealLeftAtConsumeFood_withoutFood_isNotUnfinishedWork() {
        // A diner pulled away mid-meal doesn't read its state as work with no item.
        assertEquals(ImmutableList.of(), unfinished(State.freshAtState(DINER_CONSUME_FOOD)));
    }

    @Test
    void declaresUnfinishedWork_rules() {
        assertTrue(DeclarativeJobs.declaresUnfinishedWork(1, 2, true, false));
        assertFalse(DeclarativeJobs.declaresUnfinishedWork(2, 2, true, false));
        assertFalse(DeclarativeJobs.declaresUnfinishedWork(1, 2, false, false));
        assertFalse(DeclarativeJobs.declaresUnfinishedWork(1, 2, true, true));
    }
}
