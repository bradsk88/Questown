package ca.bradj.questown.town;

import ca.bradj.questown.blocks.StatefulJobBlock;
import ca.bradj.questown.integration.minecraft.DisplaySlots;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mc.Util;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class TownWorkStatusStore extends AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> {
    public TownWorkStatusStore(DebugLogger debugLogger) {
        super(
                TownWorkStatusStore::scannablePositions,
                TownWorkStatusStore::isAirWithNoDisplay,
                TownWorkStatusStore::startingState,
                TownWorkStatusStore::processingStateWriter,
                debugLogger
        );
    }

    private static ImmutableList<BlockPos> scannablePositions(
            MCRoom room,
            Position pos
    ) {
        return ImmutableList.of(
                new BlockPos(pos.x, room.yCoord, pos.z),
                new BlockPos(pos.x, room.yCoord + 1, pos.z),
                new BlockPos(pos.x, room.yCoord - 1, pos.z)
        );
    }

    private static boolean isAirWithNoDisplay(
            ServerLevel level,
            BlockPos pos
    ) {
        return level.isEmptyBlock(pos) && DisplaySlots.at(level, pos) == null;
    }

    private static @Nullable State startingState(
            ServerLevel level,
            BlockPos pos
    ) {
        if (level.isEmptyBlock(pos)) {
            return DisplaySlots.at(level, pos) == null ? null : State.fresh();
        }
        if (ServerJobsRegistry.shouldInitializeWithState(Util.info(level), pos)) {
            return ServerJobsRegistry.getDefaultJobBlockState(level.getBlockState(pos).getBlock());
        }
        return null;
    }

    private static @Nullable Function<State, Boolean> processingStateWriter(
            ServerLevel level,
            BlockPos pos
    ) {
        if (!(level.getBlockState(pos).getBlock() instanceof StatefulJobBlock)) {
            return null;
        }
        return state -> {
            Block now = level.getBlockState(pos).getBlock();
            if (!(now instanceof StatefulJobBlock stateful)) {
                return false;
            }
            stateful.setProcessingState(level, pos, state);
            return true;
        };
    }

    @Override
    protected boolean isRestingState(
            ServerLevel level,
            BlockPos pos,
            State state
    ) {
        return level.isLoaded(pos) && ServerJobsRegistry.isRestingState(level.getBlockState(pos), state.processingState());
    }
}
