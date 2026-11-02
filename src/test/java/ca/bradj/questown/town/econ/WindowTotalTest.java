package ca.bradj.questown.town.econ;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link WindowTotal}, the per-window unmet-need count. */
class WindowTotalTest {

    @Test
    void firstWindowReturnsFullCumulative() {
        // The first call returns the whole total.
        WindowTotal w = new WindowTotal(0);
        assertEquals(10, w.next(10));
        assertEquals(0, w.next(10));   // nothing new since last observation
    }

    @Test
    void successiveWindowsReturnOnlyDeltas() {
        WindowTotal w = new WindowTotal(0);
        assertEquals(5, w.next(5));
        assertEquals(3, w.next(8));
        assertEquals(2, w.next(10));
        assertEquals(0, w.next(10));  // no change since last call
    }

    @Test
    void windowCanStartMidCumulative() {
        // The window can start from any total.
        WindowTotal w = new WindowTotal(100);
        assertEquals(0, w.next(100));
        assertEquals(25, w.next(125));
        assertEquals(15, w.next(140));
    }

    @Test
    void windowIsResumableAcrossCallsWithoutReset() {
        WindowTotal w = new WindowTotal(0);
        w.next(10);
        w.next(20);
        assertEquals(5, w.next(25));
    }

    @Test
    void windowSumsToTotalRecordsAdded() {
        // 500 needs in batches of 50: each window is 50, and they sum to 500.
        WindowTotal w = new WindowTotal(0);
        int cumulative = 0;
        for (int batch = 0; batch < 10; batch++) {
            cumulative += 50;
            assertEquals(50, w.next(cumulative), "batch " + batch);
        }
        // Nothing new: a zero window, not the running total.
        assertEquals(0, w.next(cumulative));
    }
}
