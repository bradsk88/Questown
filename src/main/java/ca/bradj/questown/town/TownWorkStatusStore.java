package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.StatefulJobBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mc.Util;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TownWorkStatusStore extends AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> {
    private static int matFactoryProbeN = 0; // TEMP DEBUG 2026-10-06
    public TownWorkStatusStore(DebugLogger debugLogger) {
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
                    if (b.toString().contains("welcome_mat")) {
                        boolean init = ServerJobsRegistry.shouldInitializeWithState(Util.info(level), pos);
                        if (matFactoryProbeN++ % 400 == 0) {
                            QT.JOB_LOGGER.warn("[PROBE:matInit] log@{} pos@{} init={}", Integer.toHexString(System.identityHashCode(debugLogger)), pos, init);
                        }
                        if (init) {
                            return ServerJobsRegistry.getDefaultJobBlockState(b);
                        }
                        return null;
                    }
                    if (ServerJobsRegistry.shouldInitializeWithState(Util.info(level), pos)) {
                        return ServerJobsRegistry.getDefaultJobBlockState(b);
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
                },
                debugLogger
        );
    }
}
