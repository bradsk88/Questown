package ca.bradj.questown.town;

/**
 * One villager's elapsed-time budget during a warp: a job cycle may only start once the warp
 * has advanced at least that job's duration since the last cycle, and starting it spends the
 * duration. Without it every warp step could bank a whole cycle - the step schedule gives each
 * duration window several sub-steps, and {@link PostDowntimeWarper} runs each one to a completed
 * cycle (2026-10-18 playtest: a fresh hunter banked 19 items in one missed day; realtime caps a
 * 2000-tick hunt at 6 a day).
 *
 * <p>A job with no timed states (duration 0) is never gated.
 */
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
