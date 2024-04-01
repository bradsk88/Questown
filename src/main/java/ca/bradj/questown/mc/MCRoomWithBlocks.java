package ca.bradj.questown.mc;

import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomWithBlocks;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public class MCRoomWithBlocks extends RoomWithBlocks<MCRoom, BlockPos, Block> {
    public MCRoomWithBlocks(
            MCRoom room,
            Map<BlockPos, Block> containedBlocks
    ) {
        super(room, containedBlocks);
    }

    public static ImmutableList<MCRoomWithBlocks> fromMatches(Collection<RoomRecipeMatch<MCRoom>> roomsMatching) {
        ImmutableList.Builder<MCRoomWithBlocks> b = ImmutableList.builder();
        roomsMatching.forEach(v -> b.add(new MCRoomWithBlocks(v.room, v.containedBlocks)));
        return b.build();
    }

    public static ImmutableList<MCRoomWithBlocks> fromRooms(
            Function<BlockPos, Block> bFn,
            Collection<MCRoom> rooms
    ) {
        ImmutableList.Builder<MCRoomWithBlocks> b = ImmutableList.builder();
        rooms.forEach(v -> b.add(new MCRoomWithBlocks(v, RecipeDetection.getBlocksInRoomV2(bFn, v, false))));
        return b.build();
    }
}
