package ca.bradj.questown.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

public interface TownFlagSubEntity {

    void runWhenOrphaned(ServerLevel sl, Block childBlock, BlockPos childPos, BlockPos flagPos);

    void addTickListener(Runnable listener);

    Block getBlock();
}
