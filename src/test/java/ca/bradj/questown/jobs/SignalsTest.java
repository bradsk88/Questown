package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static ca.bradj.questown.jobs.Signals.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignalsTest {

    @Test
    void productiveTicks_allDaytime() {
        assertEquals(11500, calculateProductiveTicks(0, 11500));
    }

    @Test
    void productiveTicks_fullDay() {
        assertEquals(PRODUCTIVE_DAY_END_TICK, calculateProductiveTicks(0, FULL_DAY_TICKS));
    }

    @Test
    void productiveTicks_startAtEvening_allNight() {
        assertEquals(0, calculateProductiveTicks(11500, 12500));
    }

    @Test
    void productiveTicks_startAtNight_allNight() {
        assertEquals(0, calculateProductiveTicks(22000, 2000));
    }

    @Test
    void productiveTicks_startAtNight_intoNextMorning() {
        // 22000 -> 24000 (night, 0 productive) + 0 -> 11500 (all productive)
        assertEquals(11500, calculateProductiveTicks(22000, 13500));
    }

    @Test
    void productiveTicks_startAtNoon_fullDay() {
        // 6000 -> 11500 (5500 productive) + 11500 -> 24000 (0) + 0 -> 6000 (6000 productive)
        assertEquals(11500, calculateProductiveTicks(6000, FULL_DAY_TICKS));
    }

    @Test
    void productiveTicks_multipleDays() {
        assertEquals(PRODUCTIVE_DAY_END_TICK * 3, calculateProductiveTicks(0, FULL_DAY_TICKS * 3));
    }

    @Test
    void productiveTicks_shortWarpDuringDay() {
        assertEquals(10, calculateProductiveTicks(1000, 10));
    }

    @Test
    void productiveTicks_shortWarpDuringNight() {
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
    void productiveTicks_partialMorning() {
        // Start at 8000 (noon), 3000 ticks -> ends at 11000, all productive
        assertEquals(3000, calculateProductiveTicks(8000, 3000));
    }

    @Test
    void productiveTicks_crossesDayNightBoundary() {
        // Start at 10000, 5000 ticks -> 10000-11500 productive (1500), 11500-15000 night (0)
        assertEquals(1500, calculateProductiveTicks(10000, 5000));
    }
}
