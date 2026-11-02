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

    /** Where a villager's feet go for a work spot: one block up on a solid spot, except a door, which is walked through. */
    public static BlockPos surfaceFeet(BlockPos feet, Predicate<BlockPos> isSolid, Predicate<BlockPos> isDoor) {
        if (isDoor.test(feet) || !isSolid.test(feet)) {
            return feet;
        }
        return feet.above();
    }
}
