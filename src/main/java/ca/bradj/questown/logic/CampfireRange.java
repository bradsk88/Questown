package ca.bradj.questown.logic;

import net.minecraft.core.BlockPos;

/**
 * Where a town's campfire may sit relative to its flag: within the campfire search radius
 * horizontally, and up to {@link #VERTICAL_TOLERANCE} blocks above or below the flag. Exact-Y
 * matching made a campfire one block up a slope invisible to the Campfire quest, with nothing
 * telling the player why (playtest r12 #2; dev 2026-10-23: scan ±2 Y).
 */
public final class CampfireRange {

    public static final int VERTICAL_TOLERANCE = 2;

    /**
     * Y offsets to scan, nearest the flag's own Y first, so a campfire level with the flag still
     * wins when there are several.
     */
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
