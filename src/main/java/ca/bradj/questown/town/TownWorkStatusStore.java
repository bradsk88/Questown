package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.StatefulJobBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TownWorkStatusStore extends AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> {
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
                    if (State.isWelcomeMat(b.toString())) {
                        // A welcome mat is a leaver work spot by definition: its
                        // "needs the leaver's supplies" condition (state 0) is intrinsic to
                        // the mat and must never depend on whether a registered job is
                        // currently claiming an interest in the block. Gating the seed on
                        // ServerJobsRegistry.shouldInitializeWithState (and its static
                        // false-cache, which outlives late job registration) left ~half the
                        // leavers' per-owner ledgers WITHOUT the mat — they never heard
                        // "the mat is state 0 for me" and the town soft-locked on it.
                        // (dev directive 2026-10-07; the NO_FORWARD_PROGRESS soft-lock.)
                        boolean init = ServerJobsRegistry.shouldInitializeWithState(Util.info(level), pos);
                        return State.welcomeMatSeedState();
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
