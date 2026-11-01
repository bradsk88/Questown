package ca.bradj.questown.jobs;

import java.util.ArrayList;
import java.util.List;

public enum Signals {
    UNDEFINED, MORNING, NOON, EVENING, NIGHT;

    public static final long FULL_DAY_TICKS = 24000;
    public static final long PRODUCTIVE_DAY_END_TICK = 11500;
    public static final long NIGHT_START_TICK = 22000;

    public record DayTime(
            long dayTime
    ) {
        public long ticksBeforeMidnight() {
            return FULL_DAY_TICKS - dayTime;
        }
    }

    public static Signals fromDayTime(DayTime gameTime) {
        long dayTime = gameTime.dayTime % FULL_DAY_TICKS;
        if (dayTime < 6000) {
            return Signals.MORNING;
        } else if (dayTime < PRODUCTIVE_DAY_END_TICK) {
            return Signals.NOON;
        } else if (dayTime < NIGHT_START_TICK) {
            return Signals.EVENING;
        } else {
            return Signals.NIGHT;
        }
    }

    public static long calculateProductiveTicks(long currentWorldTime, long totalTicks) {
        if (totalTicks <= 0) {
            return 0;
        }

        long startTime = currentWorldTime - totalTicks;
        long cursor = ((startTime % FULL_DAY_TICKS) + FULL_DAY_TICKS) % FULL_DAY_TICKS;

        long firstCycleRemaining = FULL_DAY_TICKS - cursor;
        if (totalTicks <= firstCycleRemaining) {
            return countProductiveInRange(cursor, totalTicks);
        }

        long productive = countProductiveInRange(cursor, firstCycleRemaining);
        long remaining = totalTicks - firstCycleRemaining;

        long fullCycles = remaining / FULL_DAY_TICKS;
        productive += fullCycles * PRODUCTIVE_DAY_END_TICK;
        remaining -= fullCycles * FULL_DAY_TICKS;

        productive += countProductiveInRange(0, remaining);
        return productive;
    }

    private static long countProductiveInRange(long startTick, long duration) {
        long endTick = startTick + duration;
        long productiveEnd = Math.min(endTick, PRODUCTIVE_DAY_END_TICK);
        return Math.max(0, productiveEnd - startTick);
    }

    /**
     * Maps the <em>productive</em> warp timeline (the labour budget) back onto the
     * <em>wall-clock</em> warp timeline (the passive-world-process budget) for a single warp
     * window {@code [windowEndDayTime - wallClockTicks, windowEndDayTime]}.
     * <p>
     * Productive ticks accrue only during {@code [0, PRODUCTIVE_DAY_END_TICK)} of each day;
     * wall-clock ticks accrue every tick. Nights sit at their <em>true</em> clock position (precise,
     * not smeared), so a sapling planted at dusk is credited the whole night before the next
     * work cycle. The window is capped at {@code Config.TIME_WARP_MAX_TICKS}, so the segment
     * list is O(days) — tiny.
     */
    public record ProductiveWallClockTimeline(
            List<Segment> segments,
            long productiveTotal,
            long wallClockTotal
    ) {
        /**
         * A maximal run of same-productiveness within the window. {@code productiveStart} is the
         * cumulative productive offset at the segment's start (only meaningful for productive
         * segments); {@code wallClockStart} is the cumulative wall-clock offset at the segment's start.
         */
        public record Segment(
                long wallClockStart,
                long wallClockLength,
                long productiveStart,
                boolean productive
        ) {}

        /**
         * Maps a productive offset ({@code 0..productiveTotal}) to its wall-clock offset within the
         * window. A productive offset that lands on the start of a productive segment returns the
         * wall-clock position <em>after</em> any preceding night (precise night placement). Offsets at
         * or beyond {@code productiveTotal} map to {@code wallClockTotal}.
         */
        public long wallClockOffsetAt(long productiveOffset) {
            for (Segment s : segments) {
                if (!s.productive()) {
                    continue;
                }
                if (productiveOffset < s.productiveStart() + s.wallClockLength()) {
                    return s.wallClockStart() + (productiveOffset - s.productiveStart());
                }
            }
            return wallClockTotal;
        }
    }

    public static ProductiveWallClockTimeline buildProductiveWallClockTimeline(
            long windowEndDayTime,
            long wallClockTicks
    ) {
        List<ProductiveWallClockTimeline.Segment> segments = new ArrayList<>();
        if (wallClockTicks <= 0) {
            return new ProductiveWallClockTimeline(segments, 0, 0);
        }

        long startTime = windowEndDayTime - wallClockTicks;
        long cursor = ((startTime % FULL_DAY_TICKS) + FULL_DAY_TICKS) % FULL_DAY_TICKS;
        long wallClockAccrued = 0;
        long productiveAccrued = 0;
        long remaining = wallClockTicks;

        while (remaining > 0) {
            boolean productive = cursor < PRODUCTIVE_DAY_END_TICK;
            long boundary = productive ? PRODUCTIVE_DAY_END_TICK : FULL_DAY_TICKS;
            long segLength = Math.min(boundary - cursor, remaining);

            segments.add(new ProductiveWallClockTimeline.Segment(
                    wallClockAccrued, segLength, productiveAccrued, productive
            ));

            wallClockAccrued += segLength;
            if (productive) {
                productiveAccrued += segLength;
            }
            remaining -= segLength;
            cursor += segLength;
            if (cursor >= FULL_DAY_TICKS) {
                cursor = 0;
            }
        }

        return new ProductiveWallClockTimeline(segments, productiveAccrued, wallClockAccrued);
    }
}
