package ca.bradj.questown.logic;

import net.minecraft.core.BlockPos;

/** Where a town's campfire may be: within the campfire search radius, and up to {@link #VERTICAL_TOLERANCE} blocks above or below the flag. */
public final class CampfireRange {

    public static final int VERTICAL_TOLERANCE = 2;

    /** Y offsets to scan, nearest the flag's height first. */
    public static final int[] Y_OFFSETS = {0, 1, -1, 2, -2};

    private CampfireRange() {
    }

    public static boolean contains(
            BlockPos flagPos,
            BlockPos campfirePos,
            int radius
    ) {
        int dx = Math.abs(campfirePos.getX() - flagPos.getX());
        int dy = Math.abs(campfirePos.getY() - flagPos.getY());
        int dz = Math.abs(campfirePos.getZ() - flagPos.getZ());
        return dx < radius && dz < radius && dy <= VERTICAL_TOLERANCE;
    }
}
