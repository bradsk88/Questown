package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Playtest 2026-10-17 / eating/eat_direct: dining_no_table declares {@code DONE: 0} work at its
 * terminal state (maxState 2). Once the meal reached state 2, the itemless-work branch read
 * state 2 as unfinished work and sent the diner "to the job site", which the routine skips -
 * so the diner fetched more food instead of eating.
 */
class DeclarativeJobsTerminalStateWorkTest {

    private static final int DINER_MAX_STATE = 2;

    private static Collection<Integer> unfinished(State flagSpot) {
        Collection<Supplier<Collection<String>>> rooms = ImmutableList.of(() -> ImmutableList.of("flag"));
        return DeclarativeJobs.getStatesWithUnfinishedWork(
                rooms,
                pos -> flagSpot,
                pos -> true,
                // The diner declares (zero) work at every state, DONE included.
                state -> DeclarativeJobs.declaresUnfinishedWork(state, DINER_MAX_STATE, true)
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
    void declaresUnfinishedWork_rules() {
        assertTrue(DeclarativeJobs.declaresUnfinishedWork(1, 2, true));
        assertFalse(DeclarativeJobs.declaresUnfinishedWork(2, 2, true));
        assertFalse(DeclarativeJobs.declaresUnfinishedWork(1, 2, false));
    }
}
