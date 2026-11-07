package ca.bradj.questown.town;

/** A villager's time budget during a warp: a job cycle starts only once the warp has passed its duration, and starting spends it. Untimed jobs are not limited. */
public class WarpCycleBudget {

    private long available = 0;

    public void credit(long elapsedTicks) {
        if (elapsedTicks > 0) {
            available += elapsedTicks;
        }
    }

    public boolean canStart(long cycleDuration) {
        return cycleDuration <= 0 || available >= cycleDuration;
    }

    public void spend(long cycleDuration) {
        if (cycleDuration <= 0) {
            return;
        }
        available = Math.max(0, available - cycleDuration);
    }
}
