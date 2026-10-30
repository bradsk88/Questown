package ca.bradj.questown.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.function.Predicate;

public final class WalkTargets {

    private WalkTargets() {
    }

    /** A villager can stand here: solid ground below, foot and torso cells not solid. */
    public static boolean isStandable(BlockGetter level, BlockPos bp) {
        boolean footSpotBlocked = level.getBlockState(bp).isSolid();
        boolean torsoSpotBlocked = level.getBlockState(bp.above()).isSolid();
        boolean groundSpotSolid = level.getBlockState(bp.below()).isSolid();
        return groundSpotSolid && !(footSpotBlocked || torsoSpotBlocked);
    }

    /**
     * Where a villager's feet go for a work spot: one block up when the spot is solid (standing on
     * a slab-height block), except a door, which is walked through. A closed door counts as solid,
     * so the lift put a 3x3 kitchen's cook (used from the doorway) on the door's upper half, and
     * re-applied to its own result each pass it climbed to the roof (dev playtest 2026-10-29).
     */
    public static BlockPos surfaceFeet(BlockPos feet, Predicate<BlockPos> isSolid, Predicate<BlockPos> isDoor) {
        if (isDoor.test(feet) || !isSolid.test(feet)) {
            return feet;
        }
        return feet.above();
    }
}
