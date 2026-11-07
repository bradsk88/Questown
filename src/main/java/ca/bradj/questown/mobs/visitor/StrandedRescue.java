package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;

import java.util.Collection;

/** When a villager stranded outside the town is sent back to the gate. Inside the town, a blocked path stays a can't-reach bubble. */
public final class StrandedRescue {

    static final int MARGIN_XZ = 8;
    static final int MARGIN_BELOW = 4;
    static final int WATER_STALL_TICKS = 200;
    /** Movement smaller than this doesn't count as getting closer. */
    private static final double PROGRESS_BLOCKS = 1.0;

    private int waterTicks;
    private double bestDistance;

    /** True when {@code pos} is outside the box around the town's flag, gate and doors, widened by the margins. No ceiling. */
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

    /** Call once per tick. True once the villager has spent {@link #WATER_STALL_TICKS} in water without getting a block closer. */
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
