package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.Signals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

/**
 * Unit tests for {@link BedlessLedger}: the nightfall-edge capture rule and the
 * once-per-morning drain. Pure, no Forge.
 */
class BedlessLedgerTest {

    @Test
    public void captureEdge_firesOnceWhenDayTurnsToEvening() {
        BedlessLedger ledger = new BedlessLedger();
        Assertions.assertFalse(ledger.captureEdge(Signals.MORNING));
        Assertions.assertFalse(ledger.captureEdge(Signals.NOON));
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING), "the first EVENING tick is the edge");
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING), "no re-fire while evening continues");
        Assertions.assertFalse(ledger.captureEdge(Signals.NIGHT), "night is not an edge");
        Assertions.assertFalse(ledger.captureEdge(Signals.MORNING), "dawn is not an edge");
    }

    @Test
    public void captureEdge_rearmsOnTheNextEvening() {
        BedlessLedger ledger = new BedlessLedger();
        ledger.captureEdge(Signals.NOON);
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING));
        ledger.captureEdge(Signals.NIGHT);
        ledger.captureEdge(Signals.MORNING);
        ledger.captureEdge(Signals.NOON);
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING), "the next day's evening edge fires again");
    }

    @Test
    public void captureEdge_restartMidNight_waitsForTheNextRealEvening() {
        // A server restart mid-night: the ledger's first tick is NIGHT (no capture),
        // and no spurious capture fires at dawn — only the next day's evening edge.
        BedlessLedger ledger = new BedlessLedger();
        Assertions.assertFalse(ledger.captureEdge(Signals.NIGHT));
        Assertions.assertFalse(ledger.captureEdge(Signals.MORNING));
        Assertions.assertFalse(ledger.captureEdge(Signals.NOON));
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING));
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING));
    }

    @Test
    public void captureEdge_restartMidEvening_capturesOnce() {
        // A restart mid-evening misses the original edge; the first observed
        // EVENING tick captures (best effort: who is bedless right now) exactly once.
        BedlessLedger ledger = new BedlessLedger();
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING));
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING));
    }

    @Test
    public void drain_takesTheRecordedSetAndClearsIt() {
        BedlessLedger ledger = new BedlessLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.recordBedless(Set.of(a, b));

        Set<UUID> first = ledger.drain();
        Assertions.assertEquals(Set.of(a, b), first);
        Assertions.assertTrue(ledger.drain().isEmpty(), "the second drain is empty (drain consumes)");
    }

    @Test
    public void recordBedless_replacesThePreviousCapture() {
        BedlessLedger ledger = new BedlessLedger();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ledger.recordBedless(Set.of(a, b));
        ledger.recordBedless(Set.of(a));

        Assertions.assertEquals(Set.of(a), ledger.drain(), "a later capture replaces the earlier one");
    }
}
