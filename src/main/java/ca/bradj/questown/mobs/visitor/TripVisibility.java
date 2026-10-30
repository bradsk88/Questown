package ca.bradj.questown.mobs.visitor;

/**
 * Whether a villager is drawn, from its job's per-tick "should disappear" answer. Hiding is
 * immediate; showing waits until the job has allowed it for {@link #SHOW_AFTER_TICKS} ticks in a
 * row. A hunter leaving town passes through DROPPING_LOOT for one tick between its hidden leaving
 * state and its hidden trip state, so it flickered visible -> invisible -> visible -> invisible
 * (dev playtest 2026-10-29).
 */
public final class TripVisibility {

    static final int SHOW_AFTER_TICKS = 2;

    private int allowedTicks;

    /** The visibility to apply this tick. */
    public boolean next(boolean currentlyVisible, boolean jobAllowsVisible) {
        if (!jobAllowsVisible) {
            allowedTicks = 0;
            return false;
        }
        if (currentlyVisible) {
            return true;
        }
        allowedTicks++;
        return allowedTicks >= SHOW_AFTER_TICKS;
    }
}
