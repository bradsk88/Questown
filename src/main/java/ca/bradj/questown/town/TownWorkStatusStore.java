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
                (room, pos) -> {
                    ImmutableList.Builder<BlockPos> builder = ImmutableList.builder();
                    builder.add(
                            new BlockPos(pos.x, room.yCoord, pos.z),
                            new BlockPos(pos.x, room.yCoord + 1, pos.z),
                            new BlockPos(pos.x, room.yCoord - 1, pos.z)
                    );
                    return builder.build();
                },
                LevelReader::isEmptyBlock,
                (level, pos) -> {
                    BlockState mbs = level.getBlockState(pos);
                    Block b = mbs.getBlock();
                    if (JobsRegistry.isJobBlock(level::getBlockState, pos)) {
                        return JobsRegistry.getDefaultJobBlockState(b);
                    }
                    return null;
                },
                (level, pos) -> {
                    BlockState mbs = level.getBlockState(pos);
                    Block b = mbs.getBlock();
                    if (b instanceof StatefulJobBlock sjb) {
                        return (state) -> {
                            BlockState mbs2 = level.getBlockState(pos);
                            Block b2 = mbs2.getBlock();
                            if (b2 instanceof StatefulJobBlock still) {
                                still.setProcessingState(level, pos, state);
                                return true;
                            } else {
                                return false;
                            }
                        };
                    }
                    return null;
                }
        );
    }
}
