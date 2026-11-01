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

    // ---- ProductiveWallClockTimeline: productive offset -> wall-clock offset mapping ----
    // wallClockOffsetAt(p) answers "after p productive ticks, how many WALL-CLOCK ticks have elapsed
    // (skipping past any night that precedes the p-th productive tick)?"

    private static void assertTimelineTotalsMatchProductiveTicks(long windowEnd, long wallClockTicks) {
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(windowEnd, wallClockTicks);
        assertEquals(
                calculateProductiveTicks(windowEnd, wallClockTicks), t.productiveTotal(),
                "productiveTotal must agree with calculateProductiveTicks"
        );
        assertEquals(Math.max(0, wallClockTicks), t.wallClockTotal(), "wallClockTotal == wallClockTicks");
    }

    @Test
    void timeline_dayOnly_isIdentity() {
        // Window [0, 11500] — all productive, no night.
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(11500, 11500);
        assertEquals(11500, t.productiveTotal());
        assertEquals(11500, t.wallClockTotal());
        assertEquals(0, t.wallClockOffsetAt(0));
        assertEquals(5000, t.wallClockOffsetAt(5000));
        // p at/after productiveTotal maps to wallClockTotal.
        assertEquals(11500, t.wallClockOffsetAt(11500));
        assertTimelineTotalsMatchProductiveTicks(11500, 11500);
    }

    @Test
    void timeline_openingNight_creditsNightBeforeFirstProductiveTick() {
        // Window [22000, 35500] = night [22000,24000] (2000) + morning [0,11500] (11500).
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(35500, 13500);
        assertEquals(11500, t.productiveTotal());
        assertEquals(13500, t.wallClockTotal());
        // The 0th productive tick happens only after the opening 2000-tick night.
        assertEquals(2000, t.wallClockOffsetAt(0));
        assertEquals(7000, t.wallClockOffsetAt(5000));
        assertEquals(13500, t.wallClockOffsetAt(11500));
        assertTimelineTotalsMatchProductiveTicks(35500, 13500);
    }

    @Test
    void timeline_nightInMiddle_creditsNightBetweenDays() {
        // Window [0, 35500] = day1 prod [0,11500) + night [11500,24000) + day2 prod [24000,35500).
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(35500, 35500);
        assertEquals(23000, t.productiveTotal());
        assertEquals(35500, t.wallClockTotal());
        // Day 1 productive maps 1:1.
        assertEquals(11000, t.wallClockOffsetAt(11000));
        // The first day-2 productive tick is credited the whole 12500-tick night before it.
        assertEquals(24000, t.wallClockOffsetAt(11500));
        assertEquals(24500, t.wallClockOffsetAt(12000));
        assertEquals(35500, t.wallClockOffsetAt(23000));
        assertTimelineTotalsMatchProductiveTicks(35500, 35500);
    }

    @Test
    void timeline_pureNight_hasNoProductiveTicksButWallClockTotal() {
        // Window [15000, 17000] — deep night, zero productive.
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(17000, 2000);
        assertEquals(0, t.productiveTotal());
        assertEquals(2000, t.wallClockTotal());
        // No productive ticks exist; offset 0 already sits at the end of the (night) window.
        assertEquals(2000, t.wallClockOffsetAt(0));
        assertTimelineTotalsMatchProductiveTicks(17000, 2000);
    }

    @Test
    void timeline_multiDay_creditsEachNight() {
        // Window [0, 72000] — three full days.
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(72000, 72000);
        assertEquals(11500 * 3, t.productiveTotal());
        assertEquals(72000, t.wallClockTotal());
        // Start of each day's productive window lands after that day's preceding night(s).
        assertEquals(0, t.wallClockOffsetAt(0));
        assertEquals(24000, t.wallClockOffsetAt(11500));   // day 2 morning
        assertEquals(48000, t.wallClockOffsetAt(23000));   // day 3 morning
        assertEquals(30000, t.wallClockOffsetAt(17500));   // mid day-2 (24000 + 6000)
        assertTimelineTotalsMatchProductiveTicks(72000, 72000);
    }

    @Test
    void timeline_zeroWallClockTicks_isEmpty() {
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(0, 0);
        assertEquals(0, t.productiveTotal());
        assertEquals(0, t.wallClockTotal());
        assertEquals(0, t.wallClockOffsetAt(0));
    }
}
