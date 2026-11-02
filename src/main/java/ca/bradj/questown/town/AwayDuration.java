package ca.bradj.questown.town;

/** How long the player was away, in words, for the "While you were away" summary. Under a day reads "half a day". */
public final class AwayDuration {
    private static final long TICKS_PER_DAY = 24000;

    private AwayDuration() {
    }

    public static String describe(long ticksAway) {
        long days = ticksAway / TICKS_PER_DAY;
        if (days < 1) {
            return "half a day";
        }
        if (days == 1) {
            return "1 day";
        }
        return days + " days";
    }
}
