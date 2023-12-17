package ca.bradj.questown.blocks;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface StatefulJobBlock {
    void setProcessingState(
            ServerLevel sl,
            BlockPos pp,
            AbstractWorkStatusStore.State bs);
}
