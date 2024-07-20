package ca.bradj.questown.town;

import ca.bradj.questown.blocks.StatefulJobBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TownWorkStatusStore extends AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> {
    public TownWorkStatusStore() {
        super(
                (room, pos) -> ImmutableList.of(
                        new BlockPos(pos.x, room.yCoord, pos.z),
                        // TODO: See TODO in listAllWorkspots
                        new BlockPos(pos.x, room.yCoord + 1, pos.z)
                ),
                LevelReader::isEmptyBlock,
                (level, pos) -> {
                    BlockState mbs = level.getBlockState(pos);
                    Block b = mbs.getBlock();
                    if (JobsRegistry.isJobBlock(mbs)) {
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
                }
        );
    }
}
