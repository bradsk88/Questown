package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface RoomsHolder {
    Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation roomRecipeId);

    Collection<MCRoom> getFarms();

    Collection<RoomRecipeMatch<MCRoom>> getMatches(Predicate<RoomRecipeMatch<MCRoom>> include);

    Collection<BlockPos> findMatchedRecipeBlocks(TownInterface.MatchRecipe mr);

    void registerFenceGate(BlockPos doorPos);

    void registerDoor(BlockPos doorPos);
    void deregisterDoor(BlockPos doorPos);

    Supplier<Boolean> getDebugTaskForDoor(BlockPos clickedPos);

    Supplier<Boolean> getDebugTaskForAllDoors();

    boolean isDoorRegistered(BlockPos clickedPos);

    Optional<RoomRecipeMatch<MCRoom>> computeRecipe(MCRoom r);

    ImmutableSet<TownPosition> getAllRegisteredDoors();
}
