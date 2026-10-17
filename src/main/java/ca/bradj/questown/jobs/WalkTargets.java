package ca.bradj.questown.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

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
}
