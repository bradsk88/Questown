package ca.bradj.questown.jobs;

public enum Signals {
    UNDEFINED, MORNING, NOON, EVENING, NIGHT;

    public record DayTime(
            long dayTime
    ) {
    }

    public static Signals fromDayTime(DayTime gameTime) {
        long dayTime = gameTime.dayTime % 24000;
        if (dayTime < 6000) {
            return Signals.MORNING;
        } else if (dayTime < 11500) {
            return Signals.NOON;
        } else if (dayTime < 22000) {
            return Signals.EVENING;
        } else {
            return Signals.NIGHT;
        }
    }
}
