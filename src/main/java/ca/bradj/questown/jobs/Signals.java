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

    public static boolean fitsInWorkday(DayTime start, long duration) {
        Signals end = fromDayTime(new DayTime(start.dayTime() + duration));
        if (end.compareTo(fromDayTime(start)) < 0) {
            return false;
        }
        return end == MORNING || end == NOON;
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

    /** Maps a warp's working time (the day until evening) onto its full clock time, nights included, so night-time processes get the whole night. */
    public record ProductiveWallClockTimeline(
            List<Segment> segments,
            long productiveTotal,
            long wallClockTotal
    ) {
        /** A run of all-working or all-idle time within the window, with its start on both clocks. */
        public record Segment(
                long wallClockStart,
                long wallClockLength,
                long productiveStart,
                boolean productive
        ) {}

        /** The clock offset for a working-time offset. A working offset at the start of a working segment lands after the night before it. */
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
