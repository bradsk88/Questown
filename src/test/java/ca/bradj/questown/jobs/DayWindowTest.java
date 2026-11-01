package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static ca.bradj.questown.jobs.Signals.EVENING;
import static ca.bradj.questown.jobs.Signals.FULL_DAY_TICKS;
import static ca.bradj.questown.jobs.Signals.MORNING;
import static ca.bradj.questown.jobs.Signals.NIGHT;
import static ca.bradj.questown.jobs.Signals.NOON;
import static ca.bradj.questown.jobs.Signals.fromDayTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Signals#fitsInWorkday}: work must end by the end of noon, the same day. */
class DayWindowTest {

    private static final long SWORD_HUNT_DURATION = 2000L;

    private static boolean canFit(long t, long d) {
        return Signals.fitsInWorkday(new Signals.DayTime(t), d);
    }

    @Test
    void fromDayTime_segment_boundaries() {
        // MORNING | NOON boundary is 6000
        assertEquals(MORNING, fromDayTime(new Signals.DayTime(5999)));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(6000)));
        // NOON | EVENING boundary is PRODUCTIVE_DAY_END_TICK = 11500
        assertEquals(NOON, fromDayTime(new Signals.DayTime(11499)));
        assertEquals(EVENING, fromDayTime(new Signals.DayTime(11500)));
        // EVENING | NIGHT boundary is NIGHT_START_TICK = 22000
        assertEquals(EVENING, fromDayTime(new Signals.DayTime(21999)));
        assertEquals(NIGHT, fromDayTime(new Signals.DayTime(22000)));
        // NIGHT runs to the end of the day and fromDayTime wraps by FULL_DAY_TICKS
        assertEquals(NIGHT, fromDayTime(new Signals.DayTime(23999)));
        assertEquals(MORNING, fromDayTime(new Signals.DayTime(FULL_DAY_TICKS)));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(FULL_DAY_TICKS + 6000)));
    }

    @Test
    void swordHunt_acceptanceWindow_isExactlyDawnTo907() {
        // D=2000: the hunter may only START the hunt in day-time t ∈ [0, 9500).
        // 9500 + 2000 = 11500 is exactly EVENING, so t=9500 is already rejected;
        // 9499 + 2000 = 11499 is still NOON, so t=9499 is the last accept.
        for (long t = 0; t < FULL_DAY_TICKS; t++) {
            assertEquals(t < 9500, canFit(t, SWORD_HUNT_DURATION),
                    "accept(t=" + t + "): a D=2000 hunt must fit only when started < 9500");
        }
    }

    @Test
    void swordHunt_boundary_pair_justFits_and_justMisses() {
        // 9499 + 2000 = 11499 → ends NOON (still within the day) → fits
        assertTrue(canFit(9499, SWORD_HUNT_DURATION));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(9499 + SWORD_HUNT_DURATION)));
        // 9500 + 2000 = 11500 → ends EVENING → does not fit
        assertFalse(canFit(9500, SWORD_HUNT_DURATION));
        assertEquals(EVENING, fromDayTime(new Signals.DayTime(9500 + SWORD_HUNT_DURATION)));
    }

    @Test
    void swordHunt_morningStart_fits() {
        // A MORNING start finishing into MORNING/early-NOON is fine (next >= current)
        assertTrue(canFit(1000, SWORD_HUNT_DURATION));
        assertEquals(MORNING, fromDayTime(new Signals.DayTime(1000 + SWORD_HUNT_DURATION)));
        // A MORNING start finishing into NOON is also fine — the window crosses 6000
        assertTrue(canFit(5000, SWORD_HUNT_DURATION));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(5000 + SWORD_HUNT_DURATION)));
    }

    @Test
    void swordHunt_overnightWrap_isRejected() {
        // Starts near midnight; t+D crosses into next day. next<MORNING or wrap → reject.
        // t=23500 is NIGHT; +2000 → wraps to 1500 of the next day (MORNING), but
        // MORNING < NIGHT so the "no wrap" check rejects it. The window is a single
        // contiguous in-day span, never wrapping.
        assertFalse(canFit(23500, SWORD_HUNT_DURATION));
        // A NOON/EVENING start always rejects (finishes in EVENING/NIGHT).
        assertFalse(canFit(12000, SWORD_HUNT_DURATION));
        assertFalse(canFit(22500, SWORD_HUNT_DURATION));
    }

    @Test
    void window_isContiguousInDay_notWrapped() {
        // Collecting the accepted span must produce exactly [0, 9500): one run, no wrap.
        long first = -1;
        long last = -1;
        for (long t = 0; t < FULL_DAY_TICKS; t++) {
            if (canFit(t, SWORD_HUNT_DURATION)) {
                if (first < 0) {
                    first = t;
                }
                last = t;
            }
        }
        assertEquals(0, first);
        assertEquals(9499, last);
        // And it is truly contiguous (no gaps inside the run)
        for (long t = first; t <= last; t++) {
            assertTrue(canFit(t, SWORD_HUNT_DURATION), "continuity gap at t=" + t);
        }
    }

    @Test
    void longerDurations_shrinkTheWindow() {
        // A longer hunt has a smaller window, all anchored at dawn (t=0).
        // D=4000 → t ∈ [0, 7500); D=8000 → t ∈ [0, 3500). Boundary check only.
        assertTrue(canFit(7499, 4000));
        assertFalse(canFit(7500, 4000));
        assertTrue(canFit(3499, 8000));
        assertFalse(canFit(3500, 8000));
        // D=0 (instant) still must be accepted at every in-day start that ends in
        // MORNING or NOON and does not wrap — i.e. the whole productive span [0,11500).
        for (long t = 0; t < FULL_DAY_TICKS; t++) {
            boolean expect = t < 11500;
            assertEquals(expect, canFit(t, 0), "D=0 accept(t=" + t + ")");
        }
    }

    // The pure Signals math this window rests on (boundaries + productivity end) is
    // independently pinned by SignalsTest; this class only adds the accept/reject window.
}
