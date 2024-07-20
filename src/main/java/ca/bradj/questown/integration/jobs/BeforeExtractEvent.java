package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.mobs.visitor.ItemAcceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public record BeforeExtractEvent<TOWN>(
        ServerLevel level,
        ItemAcceptor<TOWN> entity,
        BlockPos workSpot
) {
}
