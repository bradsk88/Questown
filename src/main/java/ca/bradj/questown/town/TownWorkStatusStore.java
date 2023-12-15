package ca.bradj.questown.town;

import ca.bradj.questown.blocks.StatefulJobBlock;
import ca.bradj.questown.integration.minecraft.MCCoupledHeldItem;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TownWorkStatusStore extends WorkStatusStore<BlockPos, MCCoupledHeldItem, MCRoom, ServerLevel> {
    public TownWorkStatusStore() {
        super(
                (room, pos) -> new BlockPos(pos.x, room.yCoord, pos.z),
                LevelReader::isEmptyBlock,
                (level, pos) -> {
                    BlockState mbs = level.getBlockState(pos);
                    Block b = mbs.getBlock();
                    if (JobsRegistry.isJobBlock(b)) {
                        return JobsRegistry.getDefaultJobBlockState(b);
                    }
                    return null;
                },
                (level, pos) -> {
                    BlockState mbs = level.getBlockState(pos);
                    Block b = mbs.getBlock();
                    if (b instanceof StatefulJobBlock sjb) {
                        return (state) -> sjb.setProcessingState(level, pos, state);
                    }
                    return null;
                },
                MCCoupledHeldItem::shrink
        );
    }
}
