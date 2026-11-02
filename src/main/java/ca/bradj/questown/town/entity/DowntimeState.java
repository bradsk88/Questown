package ca.bradj.questown.town.entity;

/**
 * A villager's rest state. A villager can rest once it has worked since its last rest and that
 * rest is at least {@code n} ticks old; with truly nothing to do, the time alone is enough.
 *
 * @param n        how old a rest must be before another
 * @param lastRest when it last rested, or null
 * @param lastWork when it last took real work, or null
 */
public record DowntimeState(
        long n,
        Long lastRest,
        Long lastWork
) {
    /** A new villager: hasn't rested or worked, so it works before its first rest. */
    public static DowntimeState fresh(long n) {
        return new DowntimeState(n, null, null);
    }

    public DowntimeState rest(long tick) {
        return new DowntimeState(n, tick, lastWork);
    }

    public DowntimeState work(long tick) {
        return new DowntimeState(n, lastRest, tick);
    }

    /** Ready to rest: worked since the last rest, which is at least {@code n} ticks old. */
    public boolean isReadyForRest(long now) {
        return lastRest != null
                && lastWork != null
                && lastWork > lastRest
                && now - lastRest >= n;
    }

    /** Ready to rest with nothing else to do: the last rest is at least {@code n} ticks old. */
    public boolean isReadyForIdleRest(long now) {
        return lastRest != null && now - lastRest >= n;
    }
}
