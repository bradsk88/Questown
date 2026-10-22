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
        Assertions.assertFalse(ledger.captureEdge(Signals.MORNING, true));
        Assertions.assertFalse(ledger.captureEdge(Signals.NOON, true));
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING, true), "the first EVENING tick is the edge");
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, true), "no re-fire while evening continues");
        Assertions.assertFalse(ledger.captureEdge(Signals.NIGHT, true), "night is not an edge");
        Assertions.assertFalse(ledger.captureEdge(Signals.MORNING, true), "dawn is not an edge");
    }

    @Test
    public void captureEdge_rearmsOnTheNextEvening() {
        BedlessLedger ledger = new BedlessLedger();
        ledger.captureEdge(Signals.NOON, true);
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING, true));
        ledger.captureEdge(Signals.NIGHT, true);
        ledger.captureEdge(Signals.MORNING, true);
        ledger.captureEdge(Signals.NOON, true);
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING, true), "the next day's evening edge fires again");
    }

    @Test
    public void captureEdge_restartMidNight_waitsForTheNextRealEvening() {
        // A server restart mid-night: the ledger's first tick is NIGHT (no capture),
        // and no spurious capture fires at dawn — only the next day's evening edge.
        BedlessLedger ledger = new BedlessLedger();
        Assertions.assertFalse(ledger.captureEdge(Signals.NIGHT, true));
        Assertions.assertFalse(ledger.captureEdge(Signals.MORNING, true));
        Assertions.assertFalse(ledger.captureEdge(Signals.NOON, true));
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING, true));
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, true));
    }

    @Test
    public void captureEdge_restartMidEvening_capturesOnce() {
        // A restart mid-evening misses the original edge; once the beds are known the
        // first observed EVENING tick captures exactly once.
        BedlessLedger ledger = new BedlessLedger();
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING, true));
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, true));
    }

    @Test
    public void captureEdge_restartMidEvening_waitsUntilTheBedsAreKnown() {
        // 2026-10-22 playtest: a restart in the evening captured on the first tick, before the
        // room scan had matched any bedroom, and every bedded townie got SLEEPLESS.
        BedlessLedger ledger = new BedlessLedger();
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, false), "beds not matched yet");
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, false));
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING, true), "captures once the scan has run");
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, true), "and only once");
    }

    @Test
    public void captureEdge_bedsStillUnknownAtNight_capturesThen() {
        BedlessLedger ledger = new BedlessLedger();
        ledger.captureEdge(Signals.NOON, true);
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, false));
        Assertions.assertTrue(ledger.captureEdge(Signals.NIGHT, true), "the due capture still runs at night");
    }

    @Test
    public void captureEdge_bedsUnknownUntilMorning_skipsThatNight() {
        BedlessLedger ledger = new BedlessLedger();
        ledger.captureEdge(Signals.NOON, true);
        Assertions.assertFalse(ledger.captureEdge(Signals.EVENING, false));
        Assertions.assertFalse(ledger.captureEdge(Signals.NIGHT, false));
        Assertions.assertFalse(ledger.captureEdge(Signals.MORNING, true), "a night with no capture is not captured at dawn");
        Assertions.assertFalse(ledger.captureEdge(Signals.NOON, true));
        Assertions.assertTrue(ledger.captureEdge(Signals.EVENING, true), "the next evening captures as usual");
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
