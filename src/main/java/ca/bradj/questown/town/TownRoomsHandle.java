package ca.bradj.questown.town;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.roomrecipes.Spaces;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rooms.TownRoomsMap;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class TownRoomsHandle implements RoomsHolder, ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>>, Supplier<TownFlagBlockEntity> {

    private final TownRoomsMap roomsMap = new TownRoomsMap(this);
    @Nullable
    private TownFlagBlockEntity town;
    @Nullable
    private MCRoom flagMetaRoom;

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
        this.flagMetaRoom = Spaces.metaRoomAround(t.getBlockPos(), 2);
    }

    /**
     * Only safe to call after initialize
     */
    private @NotNull TownFlagBlockEntity unsafeGetTown() {
        if (town == null) {
            throw new IllegalStateException("Town has not been initialized on quest handle yet");
        }
        return town;
    }

    @Override
    public Collection<RoomRecipeMatch<MCRoom>> getRoomsMatching(ResourceLocation recipeId) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        if (SpecialQuests.TOWN_GATE.equals(recipeId)) {
            return getWelcomeMatMetaRooms(t);
        }
        if (SpecialQuests.TOWN_FLAG.equals(recipeId)) {
            return ImmutableList.of(getFlagMetaRoom(t));
        }

        return roomsMap.getRoomsMatching(recipeId);
    }

    private RoomRecipeMatch<MCRoom> getFlagMetaRoom(TownFlagBlockEntity t) {
        MCRoom mcRoom = Spaces.metaRoomAround(t.getTownFlagBasePos(), Config.META_ROOM_DIAMETER.get());
        ImmutableMap<BlockPos, Block> blocksInRoom = RecipeDetection.getBlocksInRoom(t.getServerLevel(), mcRoom, false);
        ResourceLocation questId = SpecialQuests.TOWN_FLAG;
        return new RoomRecipeMatch<>(mcRoom, questId, blocksInRoom.entrySet());
    }

    @NotNull
    private List<RoomRecipeMatch<MCRoom>> getWelcomeMatMetaRooms(@NotNull TownFlagBlockEntity t) {
        // TODO: Cache these
        Function<MCRoom, ImmutableMap<BlockPos, Block>> fn = room -> RecipeDetection.getBlocksInRoom(
                t.getServerLevel(),
                room,
                false
        );
        return t.getWelcomeMats()
                .stream()
                .map(p -> Spaces.metaRoomAround(p, Config.META_ROOM_DIAMETER.get()))
                .map(v -> new RoomRecipeMatch<>(v, SpecialQuests.TOWN_GATE, fn.apply(v).entrySet()))
                .toList();
    }

    @Override
    public void roomRecipeCreated(MCRoom mcRoom, RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        t.roomRecipeCreated(mcRoom, mcRoomRoomRecipeMatch);
    }

    @Override
    public void roomRecipeChanged(MCRoom mcRoom, RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch, MCRoom room1, RoomRecipeMatch<MCRoom> key1) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        t.roomRecipeChanged(mcRoom, mcRoomRoomRecipeMatch, room1, key1);
    }

    @Override
    public void roomRecipeDestroyed(MCRoom mcRoom, RoomRecipeMatch<MCRoom> mcRoomRoomRecipeMatch) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        t.roomRecipeDestroyed(mcRoom, mcRoomRoomRecipeMatch);
    }

    @Override
    public TownFlagBlockEntity get() {
        return unsafeGetTown();
    }

    public TownRoomsMap getRegisteredRooms() {
        return roomsMap;
    }

    void tick(ServerLevel sl, BlockPos blockPos) {
        roomsMap.tick(sl, blockPos);
    }

    public ImmutableList<MCRoom> getAllRoomsIncludingMeta() {
        ImmutableList.Builder<MCRoom> b = ImmutableList.builder();
        b.addAll(roomsMap.getAllRooms());
        assert flagMetaRoom != null;
        b.add(flagMetaRoom);
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        getWelcomeMatMetaRooms(t).forEach(v -> b.add(v.room));
        return b.build();
    }

    public Collection<MCRoom> getFarms() {
        return roomsMap.getFarms();
    }

    @Override
    public Collection<BlockPos> findMatchedRecipeBlocks(TownInterface.MatchRecipe mr) {
        ImmutableList.Builder<BlockPos> b = ImmutableList.builder();
        for (RoomRecipeMatch<MCRoom> i : roomsMap.getAllMatches()) {
            for (Map.Entry<BlockPos, Block> j : i.getContainedBlocks().entrySet()) {
                if (mr.doesMatch(j.getValue())) {
                    b.add(j.getKey());
                }
            }
        }
        return b.build();
    }

    boolean hasEnoughBeds(long numVillagers) {
        // TODO: This returns false positives if called before entities have been loaded from tile data
        long beds = roomsMap.getAllMatches().stream()
                .flatMap(v -> v.getContainedBlocks().values().stream())
                .filter(v -> Ingredient.of(ItemTags.BEDS).test(new ItemStack(v.asItem())))
                .count();
        if (beds == 0 && numVillagers == 0) {
            return false;
        }
        return (beds / 2) >= numVillagers;
    }

    @Override
    public Collection<RoomRecipeMatch<MCRoom>> getMatches() {
        return this.roomsMap.getAllMatches();
    }

    public void registerDoor(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        roomsMap.registerDoor(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
        t.setChanged();
    }

    public void registerFenceGate(BlockPos clickedPos) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        roomsMap.registerFenceGate(Positions.FromBlockPos(clickedPos), clickedPos.getY() - t.getY());
        t.setChanged();
    }
}
