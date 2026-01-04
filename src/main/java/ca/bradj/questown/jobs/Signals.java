package ca.bradj.questown.jobs;

public enum Signals {
    UNDEFINED, MORNING, NOON, EVENING, NIGHT;

    public static final long NIGHT_START_TICK = 22000;

    public record DayTime(
            long dayTime
    ) {
        public static DayTime fromGameTime(long dayTime) {
            return new DayTime(dayTime % 24000);
        }

        public long ticksBeforeMidnight() {
            return 24000 - dayTime;
        }

        public DayTime plus(Long ticks) {
            return new DayTime((dayTime + ticks) % 24000);
        }
    }

    public static Signals fromDayTime(DayTime gameTime) {
        long dayTime = gameTime.dayTime % 24000;
        if (dayTime < 6000) {
            return Signals.MORNING;
        } else if (dayTime < 11500) {
            return Signals.NOON;
        } else if (dayTime < NIGHT_START_TICK) {
            return Signals.EVENING;
        } else {
            return Signals.NIGHT;
        }
    }
}
