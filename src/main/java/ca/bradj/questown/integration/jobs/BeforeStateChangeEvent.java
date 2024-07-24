package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.jobs.WorkSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record BeforeStateChangeEvent(
        ServerLevel level,
        WorkSpot<Integer, BlockPos> workSpot
) {
}
