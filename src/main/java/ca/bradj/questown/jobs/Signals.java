package ca.bradj.questown.jobs;

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

        long cursor = currentWorldTime % FULL_DAY_TICKS;

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
}
