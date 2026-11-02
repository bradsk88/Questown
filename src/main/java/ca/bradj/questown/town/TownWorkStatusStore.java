package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.blocks.StatefulJobBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import ca.bradj.questown.integration.minecraft.DisplaySlots;
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
                // A display stands in air and is a work spot for the display jobs.
                (level, pos) -> level.isEmptyBlock(pos) && DisplaySlots.at(level, pos) == null,
                (level, pos) -> {
                    if (level.isEmptyBlock(pos)) {
                        return DisplaySlots.at(level, pos) == null ? null : State.fresh();
                    }
                    BlockState mbs = level.getBlockState(pos);
                    Block b = mbs.getBlock();
                    if (State.isWelcomeMat(b.toString())) {
                        // A welcome mat is always a trip work spot, whether or not a job has registered interest in it.
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

    /** A job board left at state 1 by a work seeker is at rest, not stuck. */
    @Override
    protected boolean isRestingState(
            ServerLevel level,
            BlockPos pos,
            State state
    ) {
        if (state.processingState() != WorkSeekerJob.MAX_STATE || !level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).getBlock() instanceof JobBoardBlock;
    }
}
