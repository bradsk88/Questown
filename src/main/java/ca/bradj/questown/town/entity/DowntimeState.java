package ca.bradj.questown.town.entity;

/**
 * A townie's downtime (rest) state, owned by the value instead of scattered in the
 * {@code SimpleVillagerHandle}. Pure and Forge-free: it is plain state plus its own transitions
 * and readiness checks, so the "rest is a reward, not a default" rule can be read (and unit
 * tested) without a handle or a level.
 *
 * <p>The rule: a townie can rest once it has <em>earned</em> a rest. Earning a rest is two-fold:
 * <ul>
 *   <li><b>work since the last rest</b> ({@code lastWork > lastRest}) — the rest is a reward for the
 *       work done after the last rest, not a default a fresh/idle townie falls into; and</li>
 *   <li><b>time</b> ({@code now - lastRest >= n}) — that rest is old enough to be over.</li>
 * </ul>
 * Both are required for the <em>primary</em> readiness ({@link #isReadyForRest}). A fresh, never-worked
 * townie has {@code lastWork == null} ⇒ never earns a rest before working first — the shape behind the
 * village soft-lock (a fresh townie was parked in DowntimeWork before its first real job, so raw-meat
 * production never started).
 *
 * <p>The <em>fallback</em> readiness ({@link #isReadyForIdleRest}) drops the "worked since rest" gate and
 * keeps only the time gate. It is used when the board has <em>truly no work</em> and no fallback for the
 * townie, so it does not sit idle forever: a rested townie with nothing to do goes to rest even though
 * it has not earned a rest since its last one.
 *
 * @param n        the rest threshold: a rest must be at least {@code n} ticks old before another becomes
 *                 available (the {@code maxTicksBeforeDowntime} setting).
 * @param lastRest the tick of the most recent rest (NOON/day-change, exit-DowntimeWork, a sleep wake);
 *                 {@code null} if never rested.
 * @param lastWork the tick of the most recent real work adoption; {@code null} if never worked.
 */
public record DowntimeState(
        long n,
        Long lastRest,
        Long lastWork
) {
    /**
     * A fresh townie's state: has not rested and has not worked. Not ready for a rest (work first) —
     * the default in place of the old "rested long ago" that made a fresh townie downtime-ready.
     */
    public static DowntimeState fresh(long n) {
        return new DowntimeState(n, null, null);
    }

    /** They rested at {@code tick}; the last-work tick is preserved (stale now; they must work again). */
    public DowntimeState rest(long tick) {
        return new DowntimeState(n, tick, lastWork);
    }

    /** They were adopted into a real work job at {@code tick} (earning a future rest). */
    public DowntimeState work(long tick) {
        return new DowntimeState(n, lastRest, tick);
    }

    /**
     * Primary readiness: work was done <em>since</em> the last rest, and that rest is at least {@code n}
     * ticks old. This is the "rest is a reward for work since the last rest" rule (choice (b)).
     */
    public boolean isReadyForRest(long now) {
        return lastRest != null
                && lastWork != null
                && lastWork > lastRest
                && now - lastRest >= n;
    }

    /**
     * Fallback readiness: no work was available for this townie (the board already tried), and the last
     * rest is at least {@code n} ticks old. Used instead of sitting idle forever when there is truly
     * nothing to do; it drops the "worked since rest" gate but keeps the time gate (so a just-rested
     * townie does not immediately re-rest).
     */
    public boolean isReadyForIdleRest(long now) {
        return lastRest != null && now - lastRest >= n;
    }
}
