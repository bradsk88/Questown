package ca.bradj.questown.town.needs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pins the enter/exit bookkeeping behind the typed gate reasons (design:
 * {@code .scratch/gannet-top-need/unmet-need-design.md}, items 3, 4 and 6):
 * registration is idempotent per (townie, key), {@code since} is first-enter,
 * rows are bounded by the roster and clear on exit, and the batch rank is
 * townies-desc then since-asc — determined by the world state alone, not by
 * how many recompute passes retried the need (the run-2 {@code words=61}
 * failure mode).
 */
class UnmetNeedTrackerTest {

    private static final UUID ALICE = new UUID(1, 1);
    private static final UUID BOB = new UUID(2, 2);
    private static final UUID CAROL = new UUID(3, 3);
    private static final String MINER = "miner";
    private static final String COOK = "cook";

    private static final GateReason COAL_NO_ROOM =
            GateReason.classify(false, "block.questown.mineshaft", -1, List.of(), List.of(), true, "coal");
    private static final GateReason FUEL_BLOCKED =
            GateReason.classify(true, "room", 1, List.of("#fuel"), List.of(), true, "coal");
    private static final GateReason NO_REQUEST =
            GateReason.classify(true, "room", -1, List.of(), List.of(), false, "bread");

    @Test
    void firstEnterIsTrueAndRepeatsAreIdempotent() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        assertTrue(t.enter(100, MINER, ALICE, "coal", COAL_NO_ROOM));
        assertFalse(t.enter(150, MINER, ALICE, "coal", COAL_NO_ROOM));
        // One row, since is the first enter, attempts counted per pass.
        assertEquals(1, t.rows().size());
        UnmetNeedTracker.Row row = t.rows().get(0);
        assertEquals(100, row.since());
        assertEquals(2, row.attempts());
    }

    @Test
    void aNewKeyReplacesTheTowniesOldKeyForThatRoot() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(100, MINER, ALICE, "coal", COAL_NO_ROOM);
        t.enter(120, MINER, ALICE, "#fuel", FUEL_BLOCKED);
        assertEquals(1, t.rows().size());
        UnmetNeedTracker.Row row = t.rows().get(0);
        assertEquals("#fuel", row.key());
        assertEquals(120, row.since());
    }

    @Test
    void exitRootClearsOnlyThatRoot() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(100, MINER, ALICE, "coal", COAL_NO_ROOM);
        t.enter(110, COOK, BOB, "#fuel", FUEL_BLOCKED);
        t.exitRoot(MINER);
        assertEquals(1, t.rows().size());
        assertEquals(COOK, t.rows().get(0).root());
    }

    @Test
    void reentryAfterExitIsANewRegistration() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(100, MINER, ALICE, "coal", COAL_NO_ROOM);
        t.exitRoot(MINER);
        assertTrue(t.enter(200, MINER, ALICE, "coal", COAL_NO_ROOM));
        assertEquals(1, t.rows().size());
        assertEquals(200, t.rows().get(0).since());
        assertEquals(1, t.rows().get(0).attempts());
    }

    @Test
    void rankIsTowniesDescThenEarliestSince() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(200, MINER, ALICE, "coal", COAL_NO_ROOM);
        t.enter(100, MINER, BOB, "coal", COAL_NO_ROOM);
        t.enter(150, COOK, CAROL, "#fuel", FUEL_BLOCKED);
        List<UnmetNeedTracker.Need> ranked = t.rank();
        assertEquals(List.of("coal", "#fuel"), ranked.stream().map(UnmetNeedTracker.Need::key).toList());
        assertEquals(2, ranked.get(0).townies());
        assertEquals(100, ranked.get(0).since());
        assertEquals(1, ranked.get(1).townies());
        assertEquals(150, ranked.get(1).since());
    }

    @Test
    void rankTiesBreakOnKeyAscending() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(10, MINER, ALICE, "zeta", COAL_NO_ROOM);
        t.enter(10, COOK, BOB, "alpha", FUEL_BLOCKED);
        assertEquals(List.of("alpha", "zeta"), t.rank().stream().map(UnmetNeedTracker.Need::key).toList());
    }

    @Test
    void rankExcludesNoRequestAndKeylessRows() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(10, COOK, ALICE, null, NO_REQUEST);
        t.enter(12, MINER, BOB, "coal", COAL_NO_ROOM);
        List<UnmetNeedTracker.Need> ranked = t.rank();
        assertEquals(1, ranked.size());
        assertEquals("coal", ranked.get(0).key());
    }
}
