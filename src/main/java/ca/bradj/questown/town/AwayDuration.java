package ca.bradj.questown.town;

/**
 * Player-facing length of an absence for the "While you were away" summary. The summary only
 * fires after at least half a day, so a sub-day absence reads "half a day" rather than the
 * "0 days" that integer day division gave (playtest 2026-10-17).
 */
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
