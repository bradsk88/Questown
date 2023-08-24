package ca.bradj.questown.jobs;

public enum Signals {
    UNDEFINED, MORNING, NOON, EVENING, NIGHT;

    public static Signals fromGameTime(long gameTime) {
        long dayTime = gameTime % 24000;
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
