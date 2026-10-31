package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Regression: {@link RoomsWithWorkableStatefulBlocks#describe()} used to NPE when called
 * on a node the state machine never populated (an unevaluated branch of the LZCD chain).
 * The 2026-10-06 cook-soft-lock diagnosis probe hit exactly that and crashed the server
 * (ReportedException: Ticking entity → NullPointerException in describe()).
 */
class RoomsWithWorkableStatefulBlocksTest {

    private static RoomsWithWorkableStatefulBlocks<Position> dep(
            int state,
            Map<Position, Integer> spotStatuses,
            Map<Position, Boolean> jobBlocks
    ) {
        Rooms<Position, Room> rooms = new Rooms<>(spotStatuses, Map.of(), jobBlocks);
        return new RoomsWithWorkableStatefulBlocks<>(state, () -> rooms, Object::toString);
    }

    @Test
    void describeSafeBeforePopulate() {
        RoomsWithWorkableStatefulBlocks<Position> d = dep(1, Map.of(), Map.of());
        Assertions.assertTrue(
                d.describe().contains("not populated"),
                "expected the not-populated sentinel, got: " + d.describe()
        );
    }

    @Test
    void describeAfterPopulateNoMatch() {
        RoomsWithWorkableStatefulBlocks<Position> d = dep(1, Map.of(), Map.of());
        Populated<WithReason<Boolean>> p = d.populate();
        Assertions.assertFalse(p.value().value(), "no spots -> false");
        Assertions.assertTrue(d.describe().contains("no spots found"));
    }

    @Test
    void describeAfterPopulateWithMatch() {
        Position pos = new Position(0, 0);
        RoomsWithWorkableStatefulBlocks<Position> d =
                dep(1, Map.of(pos, 1), Map.of(pos, true));
        Populated<WithReason<Boolean>> p = d.populate();
        Assertions.assertTrue(p.value().value(), "matching workable block -> true");
        Assertions.assertTrue(d.describe().contains("true"));
    }
}
