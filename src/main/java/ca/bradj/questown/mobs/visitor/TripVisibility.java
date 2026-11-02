package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Whether a villager is drawn. Hiding waits about a second, so a leaving villager reaches its work spot; showing waits a few ticks, so a one-tick status between trips doesn't flash it. */
public final class TripVisibility {

    static final int SHOW_AFTER_TICKS = 2;
    static final int HIDE_AFTER_TICKS = 20;

    private int allowedTicks;
    private int hiddenTicks;

    /** Where the leave and return sparkles go: above the work spot, else at the villager. */
    public static Vec3 sparkleAt(boolean nowVisible, Vec3 entityPos, @Nullable BlockPos workSpot) {
        if (nowVisible || workSpot == null) {
            return entityPos;
        }
        return Vec3.atBottomCenterOf(workSpot.above());
    }

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
