package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiFunction;

public class TownWorldInteraction {

    private final UnsafeTown town = new UnsafeTown(getClass());

    TownWorldInteraction() {
    }

    void init(TownFlagBlockEntity t) {
        town.initialize(t);
    }

    void swapBlocks(
            ServerLevel level,
            RoomRecipeMatch<MCRoom> match
    ) {
        ImmutableMap<ResourceLocation, BiFunction<ServerLevel, RoomRecipeMatch<MCRoom>, Void>> swaps = ImmutableMap.of(Questown.ResourceLocation("job_board"),
                this::swapJobBoardSign
        );
        for (ResourceLocation recipeID : match.getRecipeIDs()) {
            BiFunction<ServerLevel, RoomRecipeMatch<MCRoom>, Void> swap = swaps.get(recipeID);
            if (swap != null) {
                swap.apply(level, match);
            }
        }
    }

    private Void swapJobBoardSign(
            ServerLevel level,
            RoomWithBlocks<MCRoom, BlockPos, Block> room
    ) {
        BlockPredicate predicate = BlockPredicate.Builder.block().of(BlockTags.SIGNS).build();
        for (Map.Entry<BlockPos, Block> e : room.containedBlocks.entrySet()) {
            if (!predicate.matches(level, e.getKey())) {
                continue;
            }
            Direction value = Direction.EAST;
            try {
                value = Util.rotationToDirection(level.getBlockState(e.getKey()).getValue(StandingSignBlock.ROTATION));
            } catch (IllegalArgumentException x) {
                try {
                    value = level.getBlockState(e.getKey()).getValue(WallSignBlock.FACING).getCounterClockWise();
                } catch (IllegalArgumentException x2) {
                    QT.FLAG_LOGGER.error("Could not find valid direction for sign");
                }
            }
            level.setBlockAndUpdate(
                    e.getKey(),
                    BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState()
                                              .setValue(HorizontalDirectionalBlock.FACING, value)
            );
            @NotNull TownFlagBlockEntity t = town.getUnsafe();
            t.registerJobsBoard(e.getKey());
            t.jobHandle.setJobBlockState(e.getKey(), State.freshAtState(WorkSeekerJob.MAX_STATE));
        }
        return null;
    }

}
