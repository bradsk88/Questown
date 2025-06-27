package ca.bradj.questown.jobs;

import net.minecraft.core.BlockPos;

public interface IsJobBlock {

    boolean test(
            WorkLocation.BlockInfo blockState,
            BlockPos blockPos,
            boolean jobAlreadyActive
    );
}
