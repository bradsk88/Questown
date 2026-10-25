package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;

import java.util.Collection;

/**
 * When a villager stranded outside the town gets poofed back to the gate (r20 playtest: one fell off
 * a cliff into water and could not get home). Inside the town a blocked path is usually something
 * the player built, so it stays a can't-reach bubble (ADR-0011); outside, there is nothing for the
 * player to fix.
 */
public final class StrandedRescue {

    /** How far past the outermost anchor, sideways, still counts as the town. */
    static final int MARGIN_XZ = 8;
    /** How far below the lowest anchor still counts as the town; deeper is a fall. */
    static final int MARGIN_BELOW = 4;
    /** Ten seconds in water without getting closer to the target. */
    static final int WATER_STALL_TICKS = 200;
    /** Progress smaller than this (drifting, bobbing) does not count as getting closer. */
    private static final double PROGRESS_BLOCKS = 1.0;

    private int waterTicks;
    private double bestDistance;

    /**
     * True when {@code pos} is outside the box around the town's anchors (flag, gate, doors),
     * widened by {@link #MARGIN_XZ} sideways and {@link #MARGIN_BELOW} downward. No ceiling: a
     * villager above the town is not stranded.
     */
    public static boolean isOutsideTown(BlockPos pos, Collection<BlockPos> anchors) {
        if (anchors.isEmpty()) {
            return false;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        for (BlockPos a : anchors) {
            minX = Math.min(minX, a.getX());
            maxX = Math.max(maxX, a.getX());
            minZ = Math.min(minZ, a.getZ());
            maxZ = Math.max(maxZ, a.getZ());
            minY = Math.min(minY, a.getY());
        }
        if (pos.getX() < minX - MARGIN_XZ || pos.getX() > maxX + MARGIN_XZ) {
            return true;
        }
        if (pos.getZ() < minZ - MARGIN_XZ || pos.getZ() > maxZ + MARGIN_XZ) {
            return true;
        }
        return pos.getY() < minY - MARGIN_BELOW;
    }

    /**
     * Call once per tick. True once the villager has been in water for {@link #WATER_STALL_TICKS}
     * without getting at least a block closer to its target.
     */
    public boolean observeWater(boolean inWater, double distanceToTarget) {
        if (!inWater) {
            reset();
            return false;
        }
        if (waterTicks == 0 || distanceToTarget < bestDistance - PROGRESS_BLOCKS) {
            bestDistance = distanceToTarget;
            waterTicks = 1;
            return false;
        }
        waterTicks++;
        return waterTicks >= WATER_STALL_TICKS;
    }

    public void reset() {
        waterTicks = 0;
    }
}
