package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static ca.bradj.questown.jobs.Signals.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SignalsTest {

    // Window is [currentWorldTime - totalTicks, currentWorldTime]; work hours run from 0 to 11500 each day.

    @Test
    void productiveTicks_windowEntirelyInNight() {
        // Window [12000, 23000]: all night.
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
        assertEquals(0, calculateProductiveTicks(22000, 2000));
    }

    @Test
    void productiveTicks_nightIntoMorning() {
        assertEquals(11500, calculateProductiveTicks(35500, 13500));
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
    void productiveTicks_windowInMorning() {
        assertEquals(3000, calculateProductiveTicks(8000, 3000));
    }

    @Test
    void productiveTicks_windowCrossesDayNightBoundary() {
        assertEquals(5000, calculateProductiveTicks(10000, 5000));
    }

    @Test
    void productiveTicks_windowCrossesProductiveEnd() {
        assertEquals(1500, calculateProductiveTicks(15000, 5000));
    }

    @Test
    void productiveTicks_eveningToMorning_sleepScenario() {
        // Window [12000, 23000]: all night.
        assertEquals(0, calculateProductiveTicks(23000, 11000));
    }

    @Test
    void productiveTicks_afternoonThroughNightToMorning() {
        assertEquals(1500, calculateProductiveTicks(24000, 14000));
    }

    @Test
    void productiveTicks_wrapsAroundMidnight() {
        assertEquals(1000, calculateProductiveTicks(25000, 2000));
    }

    // ProductiveWallClockTimeline: after p working ticks, how many clock ticks have passed, nights included.

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
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(11500, 11500);
        assertEquals(11500, t.productiveTotal());
        assertEquals(11500, t.wallClockTotal());
        assertEquals(0, t.wallClockOffsetAt(0));
        assertEquals(5000, t.wallClockOffsetAt(5000));
        assertEquals(11500, t.wallClockOffsetAt(11500));
        assertTimelineTotalsMatchProductiveTicks(11500, 11500);
    }

    @Test
    void timeline_openingNight_creditsNightBeforeFirstProductiveTick() {
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(35500, 13500);
        assertEquals(11500, t.productiveTotal());
        assertEquals(13500, t.wallClockTotal());
        assertEquals(2000, t.wallClockOffsetAt(0));
        assertEquals(7000, t.wallClockOffsetAt(5000));
        assertEquals(13500, t.wallClockOffsetAt(11500));
        assertTimelineTotalsMatchProductiveTicks(35500, 13500);
    }

    @Test
    void timeline_nightInMiddle_creditsNightBetweenDays() {
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(35500, 35500);
        assertEquals(23000, t.productiveTotal());
        assertEquals(35500, t.wallClockTotal());
        assertEquals(11000, t.wallClockOffsetAt(11000));
        assertEquals(24000, t.wallClockOffsetAt(11500));
        assertEquals(24500, t.wallClockOffsetAt(12000));
        assertEquals(35500, t.wallClockOffsetAt(23000));
        assertTimelineTotalsMatchProductiveTicks(35500, 35500);
    }

    @Test
    void timeline_pureNight_hasNoProductiveTicksButWallClockTotal() {
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(17000, 2000);
        assertEquals(0, t.productiveTotal());
        assertEquals(2000, t.wallClockTotal());
        assertEquals(2000, t.wallClockOffsetAt(0));
        assertTimelineTotalsMatchProductiveTicks(17000, 2000);
    }

    @Test
    void timeline_multiDay_creditsEachNight() {
        ProductiveWallClockTimeline t = buildProductiveWallClockTimeline(72000, 72000);
        assertEquals(11500 * 3, t.productiveTotal());
        assertEquals(72000, t.wallClockTotal());
        assertEquals(0, t.wallClockOffsetAt(0));
        assertEquals(24000, t.wallClockOffsetAt(11500));   // day 2 morning
        assertEquals(48000, t.wallClockOffsetAt(23000));
        assertEquals(30000, t.wallClockOffsetAt(17500));
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
