package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static ca.bradj.questown.jobs.Signals.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignalsTest {

    // Window [currentWorldTime - totalTicks, currentWorldTime]
    // Productive hours: tick 0 to PRODUCTIVE_DAY_END_TICK (11500) each day

    @Test
    void productiveTicks_windowEntirelyInNight() {
        // Window [12000, 23000] — all past productive hours
        assertEquals(0, calculateProductiveTicks(23000, 11000));
    }

    @Test
    void productiveTicks_fullDay() {
        assertEquals(PRODUCTIVE_DAY_END_TICK, calculateProductiveTicks(0, FULL_DAY_TICKS));
    }

    @Test
    void productiveTicks_fullDayFromNoon() {
        assertEquals(PRODUCTIVE_DAY_END_TICK, calculateProductiveTicks(6000, FULL_DAY_TICKS));
    }

    @Test
    void productiveTicks_nightOnly() {
        // Window [20000, 22000] — deep night
        assertEquals(0, calculateProductiveTicks(22000, 2000));
    }

    @Test
    void productiveTicks_nightIntoMorning() {
        // Window [22000, 35500] = [22000, 24000] + [0, 11500]
        // Night portion: 22000-24000 (0 productive)
        // Morning portion: 0-11500 (11500 productive)
        assertEquals(11500, calculateProductiveTicks(35500, 13500));
    }

    @Test
    void productiveTicks_multipleDays() {
        assertEquals(PRODUCTIVE_DAY_END_TICK * 3, calculateProductiveTicks(0, FULL_DAY_TICKS * 3));
    }

    @Test
    void productiveTicks_shortWarpDuringDay() {
        // Window [990, 1000] — all productive
        assertEquals(10, calculateProductiveTicks(1000, 10));
    }

    @Test
    void productiveTicks_shortWarpDuringNight() {
        // Window [14990, 15000] — all night
        assertEquals(0, calculateProductiveTicks(15000, 10));
    }

    @Test
    void productiveTicks_zeroTicks() {
        assertEquals(0, calculateProductiveTicks(0, 0));
    }

    @Test
    void productiveTicks_negativeTicks() {
        assertEquals(0, calculateProductiveTicks(0, -100));
    }

    @Test
    void productiveTicks_windowInMorning() {
        // Window [5000, 8000] — all productive
        assertEquals(3000, calculateProductiveTicks(8000, 3000));
    }

    @Test
    void productiveTicks_windowCrossesDayNightBoundary() {
        // Window [5000, 10000] — all productive (ends before 11500)
        assertEquals(5000, calculateProductiveTicks(10000, 5000));
    }

    @Test
    void productiveTicks_windowCrossesProductiveEnd() {
        // Window [10000, 15000] — 10000-11500 productive (1500), 11500-15000 night (0)
        assertEquals(1500, calculateProductiveTicks(15000, 5000));
    }

    @Test
    void productiveTicks_eveningToMorning_sleepScenario() {
        // Window [12000, 23000] — entirely past productive hours, all night
        // This is the campfire sleep bug scenario
        assertEquals(0, calculateProductiveTicks(23000, 11000));
    }

    @Test
    void productiveTicks_afternoonThroughNightToMorning() {
        // Window [10000, 24000] — 10000-11500 productive (1500), rest is night/wrap
        assertEquals(1500, calculateProductiveTicks(24000, 14000));
    }

    @Test
    void productiveTicks_wrapsAroundMidnight() {
        // Window [23000, 25000] = [23000, 24000] night + [0, 1000] morning
        assertEquals(1000, calculateProductiveTicks(25000, 2000));
    }
}
