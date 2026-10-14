package ca.bradj.questown.town.needs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 *
 * <p>Union semantics (2026-10-15 ruling, {@code .scratch/playtest-2026-10-14/
 * cook-status-blocker-surfacing-10-14.md}): a townie's live needs for a root
 * are a UNION, not the best-scoring job's single key — a new key joins the
 * townie's live set instead of replacing it, and a key exits when it drops
 * out of the pass's live set ({@link UnmetNeedTracker#applyPass}), not only
 * when the whole root clears. The player-facing hint is the townie's oldest
 * live key ({@link UnmetNeedTracker#primaryKey}), stable across recompute
 * passes.
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
    private static final GateReason NO_REQUEST_KEY =
            GateReason.classify(true, "room", 1, List.of("bread"), List.of(), true, "bread");

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
    void aNewKeyJoinsTheTowniesLiveNeedsForThatRoot() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(100, MINER, ALICE, "coal", COAL_NO_ROOM);
        t.enter(120, MINER, ALICE, "#fuel", FUEL_BLOCKED);
        // The union: both keys stay live (2026-10-15 ruling - the old
        // replace-on-new-key made the named blocker flap with the pass's
        // best-scoring job instead of the world state).
        assertEquals(2, t.rows().size());
        Map<String, Long> sinceByKey = new java.util.HashMap<>();
        for (UnmetNeedTracker.Row r : t.rows()) {
            sinceByKey.put(r.key(), r.since());
        }
        assertEquals(100L, sinceByKey.get("coal"));
        assertEquals(120L, sinceByKey.get("#fuel"));
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

    @Test
    void exitKeyClearsOnlyThatKeyForThatRoot() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(100, MINER, ALICE, "coal", COAL_NO_ROOM);
        t.enter(120, MINER, ALICE, "#fuel", FUEL_BLOCKED);
        t.enter(130, COOK, BOB, "#fuel", FUEL_BLOCKED);
        t.exitKey(MINER, "coal");
        assertEquals(2, t.rows().size());
        // The miner's coal row is gone; the miner's #fuel and the cook's #fuel survive.
        for (UnmetNeedTracker.Row r : t.rows()) {
            assertFalse("coal".equals(r.key()) && MINER.equals(r.root()));
        }
    }

    @Test
    void reentryAfterExitKeyIsANewRegistration() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(100, MINER, ALICE, "coal", COAL_NO_ROOM);
        t.exitKey(MINER, "coal");
        assertTrue(t.enter(200, MINER, ALICE, "coal", COAL_NO_ROOM));
        assertEquals(1, t.rows().size());
        assertEquals(200, t.rows().get(0).since());
        assertEquals(1, t.rows().get(0).attempts());
    }

    @Test
    void applyPassEntersLiveKeysAndExitsStaleOnes() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        List<UnmetNeedTracker.FirstEnter> first = t.applyPass(
                100, MINER, List.of(ALICE), Map.of("coal", COAL_NO_ROOM, "#fuel", FUEL_BLOCKED));
        assertEquals(2, first.size());
        assertEquals(2, t.rows().size());

        // Second pass: #fuel is satisfied, coal is not - coal survives with its
        // original since and a grown attempts counter; no new registrations.
        List<UnmetNeedTracker.FirstEnter> second = t.applyPass(
                200, MINER, List.of(ALICE), Map.of("coal", COAL_NO_ROOM));
        assertTrue(second.isEmpty());
        assertEquals(1, t.rows().size());
        UnmetNeedTracker.Row coal = t.rows().get(0);
        assertEquals("coal", coal.key());
        assertEquals(100, coal.since());
        assertEquals(2, coal.attempts());
    }

    @Test
    void applyPassWithAnEmptyLiveSetClearsTheRoot() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.applyPass(100, MINER, List.of(ALICE), Map.of("coal", COAL_NO_ROOM));
        t.applyPass(200, MINER, List.of(ALICE), Map.of());
        assertTrue(t.rows().isEmpty());
    }

    @Test
    void primaryKeyIsTheOldestLiveKeyTieBrokenByKeyAscending() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(100, MINER, ALICE, "#questown:raw_food", FUEL_BLOCKED);
        t.enter(100, MINER, ALICE, "#minecraft:coals", COAL_NO_ROOM);
        // Same since: the earlier key in ascending order wins - the hint is
        // deterministic given the world state, not the pass's argmax.
        assertEquals("#minecraft:coals", t.primaryKey(MINER, ALICE));
        // A later key is secondary until the oldest one exits.
        t.enter(150, MINER, ALICE, "bread", NO_REQUEST_KEY);
        assertEquals("#minecraft:coals", t.primaryKey(MINER, ALICE));
        t.exitKey(MINER, "#minecraft:coals");
        assertEquals("#questown:raw_food", t.primaryKey(MINER, ALICE));
    }

    @Test
    void primaryKeyIsNullOrKeylessOnly() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.enter(10, COOK, ALICE, null, NO_REQUEST);
        assertNull(t.primaryKey(COOK, ALICE));
        // Nothing registered at all.
        assertNull(t.primaryKey(MINER, BOB));
    }

    @Test
    void rankCountsATownieInEveryLiveKey() {
        UnmetNeedTracker t = new UnmetNeedTracker();
        t.applyPass(100, MINER, List.of(ALICE, BOB), Map.of("coal", COAL_NO_ROOM, "#fuel", FUEL_BLOCKED));
        t.applyPass(110, COOK, List.of(CAROL), Map.of("#fuel", FUEL_BLOCKED));
        List<UnmetNeedTracker.Need> ranked = t.rank();
        // coal: 2 townies; #fuel: 3 (both miners + the cook) - the union, not
        // the best-scoring job's single key.
        assertEquals(List.of("#fuel", "coal"), ranked.stream().map(UnmetNeedTracker.Need::key).toList());
        assertEquals(3, ranked.get(0).townies());
        assertEquals(2, ranked.get(1).townies());
    }
}
