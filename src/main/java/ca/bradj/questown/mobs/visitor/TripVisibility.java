package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a villager is drawn, from its job's per-tick "should disappear" answer. Hiding waits until
 * the job has asked for it {@link #HIDE_AFTER_TICKS} ticks in a row (about a second, so a leaving
 * villager reaches its work spot first). Showing waits until the job has allowed it for
 * {@link #SHOW_AFTER_TICKS} ticks in a row: a hunter leaving town passes through DROPPING_LOOT for
 * one tick between its hidden leaving state and its hidden trip state.
 */
public final class TripVisibility {

    static final int SHOW_AFTER_TICKS = 2;
    static final int HIDE_AFTER_TICKS = 20;

    private int allowedTicks;
    private int hiddenTicks;

    /**
     * Where the leave/return sparkles go: above the work spot a villager leaves from, else where it
     * stands.
     */
    public static Vec3 sparkleAt(boolean nowVisible, Vec3 entityPos, @Nullable BlockPos workSpot) {
        if (nowVisible || workSpot == null) {
            return entityPos;
        }
        return Vec3.atBottomCenterOf(workSpot.above());
    }

    /** The visibility to apply this tick. */
    public boolean next(boolean currentlyVisible, boolean jobAllowsVisible) {
        if (!jobAllowsVisible) {
            allowedTicks = 0;
            hiddenTicks++;
            return currentlyVisible && hiddenTicks < HIDE_AFTER_TICKS;
        }
        hiddenTicks = 0;
        if (currentlyVisible) {
            return true;
        }
        allowedTicks++;
        return allowedTicks >= SHOW_AFTER_TICKS;
    }
}
