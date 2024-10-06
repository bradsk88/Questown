package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class RoomRecipeMatches {
    @SuppressWarnings("ALL")
    public static RoomRecipeMatch<MCRoom> unsafe(@NotNull IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?> in) {
        Iterable containedBlocks = (Iterable) in.getContainedBlocks().entrySet();
        return new RoomRecipeMatch<>(in.getRoom(), in.getRecipeID(), containedBlocks);
    }
}
