package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.rooms.RoomDoors;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatches;
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

    /** The registered-room map (un-matched registered rooms included). */
    TownRoomsMap getRegisteredRooms();

    Collection<BlockPos> findMatchedRecipeBlocks(TownInterface.MatchRecipe mr);

    void registerFenceGate(BlockPos doorPos);

    void registerDoor(BlockPos doorPos);

    record Deregistration(BlockPos doorPos, String reason) {}

    void deregisterDoor(Deregistration dereg);

    Supplier<Boolean> getDebugTaskForDoor(BlockPos clickedPos);

    Supplier<Boolean> getDebugTaskForAllDoors();

    boolean isDoorRegistered(BlockPos clickedPos);

    Optional<RoomRecipeMatches<MCRoom>> computeRecipe(MCRoom r);

    ImmutableSet<TownPosition> getAllRegisteredDoors();

    void registerBlockAsRoom(ResourceLocation blockId, BlockPos clickedPos);

    /**
     * The doorway of the registered room that holds {@code pos} (e.g. a bed): where a townie
     * stands when there is no free floor beside that block.
     */
    default Optional<BlockPos> findDoorOfRoomContaining(BlockPos pos) {
        return RoomDoors.doorOfRoomContaining(getRegisteredRooms().getAllRooms(), pos);
    }
}
