package ca.bradj.questown.town.econ;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure {@link WindowTotal} helper used by
 * {@link NoMCEconomics#getUnmetNeedsWindowTotal}. NoMCEconomics itself cannot be
 * instantiated in a plain unit test (its constructor calls
 * {@code Config.ECONOMIC_RECORDS_DEPTH.get()}, which requires a loaded
 * ForgeConfigSpec); the window math factored out here is the piece the daily
 * report actually depends on, so it is tested on its own.
 */
class WindowTotalTest {

    @Test
    void firstWindowReturnsFullCumulative() {
        // A freshly-created window has no prior observation, so the first
        // next(c) returns c itself (the whole cumulative so far).
        WindowTotal w = new WindowTotal(0);
        assertEquals(10, w.next(10));
        assertEquals(0, w.next(10));   // nothing new since last observation
    }

    @Test
    void successiveWindowsReturnOnlyDeltas() {
        WindowTotal w = new WindowTotal(0);
        assertEquals(5, w.next(5));
        assertEquals(3, w.next(8));   // 8 - 5
        assertEquals(2, w.next(10));  // 10 - 8
        assertEquals(0, w.next(10));  // no change since last call
    }

    @Test
    void windowCanStartMidCumulative() {
        // The window can be seeded to any starting point (e.g. the cumulative
        // count already known from an earlier observation).
        WindowTotal w = new WindowTotal(100);
        assertEquals(0, w.next(100));          // nothing new since 100
        assertEquals(25, w.next(125));
        assertEquals(15, w.next(140));
    }

    @Test
    void windowIsResumableAcrossCallsWithoutReset() {
        // No reset method: the window simply follows the cumulative forward.
        WindowTotal w = new WindowTotal(0);
        w.next(10);
        w.next(20);
        // After two calls the baseline is at 20; a further 5 gives 25.
        assertEquals(5, w.next(25));
    }

    @Test
    void windowSumsToTotalRecordsAdded() {
        // 500 unmet needs register over a run, in batches of 50. The windows
        // seen across those batches sum to 500, and each batch's window is 50.
        WindowTotal w = new WindowTotal(0);
        int cumulative = 0;
        for (int batch = 0; batch < 10; batch++) {
            cumulative += 50;
            assertEquals(50, w.next(cumulative), "batch " + batch);
        }
        // No new records since the last batch: a zero window, not the running
        // total (this is the case the old cumulative-since-start report failed
        // on: demand was pinned at the EvictingQueue depth).
        assertEquals(0, w.next(cumulative));
    }
}
