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

class DayWindowTest {

    private static final long SWORD_HUNT_DURATION = 2000L;

    private static boolean canFit(long t, long d) {
        return Signals.fitsInWorkday(new Signals.DayTime(t), d);
    }

    @Test
    void fromDayTime_segment_boundaries() {
        assertEquals(MORNING, fromDayTime(new Signals.DayTime(5999)));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(6000)));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(11499)));
        assertEquals(EVENING, fromDayTime(new Signals.DayTime(11500)));
        assertEquals(EVENING, fromDayTime(new Signals.DayTime(21999)));
        assertEquals(NIGHT, fromDayTime(new Signals.DayTime(22000)));
        assertEquals(NIGHT, fromDayTime(new Signals.DayTime(23999)));
        assertEquals(MORNING, fromDayTime(new Signals.DayTime(FULL_DAY_TICKS)));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(FULL_DAY_TICKS + 6000)));
    }

    @Test
    void swordHunt_acceptanceWindow_isExactlyDawnTo907() {
        // A 2000-tick hunt may start only before 9500: 9500 + 2000 is evening.
        for (long t = 0; t < FULL_DAY_TICKS; t++) {
            assertEquals(t < 9500, canFit(t, SWORD_HUNT_DURATION),
                    "accept(t=" + t + "): a D=2000 hunt must fit only when started < 9500");
        }
    }

    @Test
    void swordHunt_boundary_pair_justFits_and_justMisses() {
        assertTrue(canFit(9499, SWORD_HUNT_DURATION));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(9499 + SWORD_HUNT_DURATION)));
        assertFalse(canFit(9500, SWORD_HUNT_DURATION));
        assertEquals(EVENING, fromDayTime(new Signals.DayTime(9500 + SWORD_HUNT_DURATION)));
    }

    @Test
    void swordHunt_morningStart_fits() {
        assertTrue(canFit(1000, SWORD_HUNT_DURATION));
        assertEquals(MORNING, fromDayTime(new Signals.DayTime(1000 + SWORD_HUNT_DURATION)));
        assertTrue(canFit(5000, SWORD_HUNT_DURATION));
        assertEquals(NOON, fromDayTime(new Signals.DayTime(5000 + SWORD_HUNT_DURATION)));
    }

    @Test
    void swordHunt_overnightWrap_isRejected() {
        // A start near midnight wraps into the next morning, which is rejected.
        assertFalse(canFit(23500, SWORD_HUNT_DURATION));
        assertFalse(canFit(12000, SWORD_HUNT_DURATION));
        assertFalse(canFit(22500, SWORD_HUNT_DURATION));
    }

    @Test
    void window_isContiguousInDay_notWrapped() {
        // The accepted starts are exactly [0, 9500).
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
        for (long t = first; t <= last; t++) {
            assertTrue(canFit(t, SWORD_HUNT_DURATION), "continuity gap at t=" + t);
        }
    }

    @Test
    void longerDurations_shrinkTheWindow() {
        // Longer hunts have smaller windows, all starting at dawn.
        assertTrue(canFit(7499, 4000));
        assertFalse(canFit(7500, 4000));
        assertTrue(canFit(3499, 8000));
        assertFalse(canFit(3500, 8000));
        // Instant work fits any start in the working day.
        for (long t = 0; t < FULL_DAY_TICKS; t++) {
            boolean expect = t < 11500;
            assertEquals(expect, canFit(t, 0), "D=0 accept(t=" + t + ")");
        }
    }

}
