package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;
import java.util.stream.Stream;

public class TownState {

    private Collection<RoomRecipe> activeQuests = new ArrayList<>();
    private final Map<Position, RoomRecipe> activeRecipes = new HashMap<>();
    private final Map<Position, Room> rooms = new HashMap<>();
    private boolean initialized;

    public boolean canAttractVisitors() {
        return false;
    }

    public boolean roomDoorExistsAt(Position doorPos) {
        return activeRecipes.containsKey(doorPos);
    }

    public void setRecipeAtDoorPosition(
            Position doorPos,
            RoomRecipe roomRecipe
    ) {
        this.activeRecipes.put(doorPos, roomRecipe);
    }

    public Optional<RoomRecipe> getRecipeAtDoorPos(Position doorPos) {
        if (activeRecipes.containsKey(doorPos)) {
            return Optional.of(activeRecipes.get(doorPos));
        }
        return Optional.empty();
    }

    public RoomRecipe unsetRecipeAtDoorPos(Position doorPos) {
        return activeRecipes.remove(doorPos);
    }

    public Collection<RoomRecipe> getQuests() {
        return ImmutableList.copyOf(activeQuests);
    }

    public boolean hasRecipe(RoomRecipe quest) {
        return activeRecipes.containsValue(quest);
    }

    public void clearQuest(RoomRecipe quest) {
        // TODO: Instead of removing, mark as "done"
        //  That way, if the room is destroyed, the quest can be re-activated
        activeQuests.remove(quest);
    }

    public void tryInitialize(RecipeManager recipeManager) {
        if (this.initialized) {
            return;
        }

        Questown.LOGGER.debug("Initializing town state");

        // TODO: Read this from config instead
        Collection<ResourceLocation> townInitRecipe = new ArrayList<>();
        townInitRecipe.add(new ResourceLocation(Questown.MODID, "crafting_room"));

        Stream<Recipe<?>> recipes = recipeManager.getRecipes()
                .stream()
                .filter(v -> v instanceof RoomRecipe)
                .filter(v -> townInitRecipe.contains(v.getId()));
        recipes.forEach(r -> {
            Questown.LOGGER.debug("Adding quest: " + r.getId());
            this.activeQuests.add((RoomRecipe) r);
        });

        this.initialized = true;
    }

    public Optional<Room> getDetectedRoom(Position doorPos) {
        if (rooms.containsKey(doorPos)) {
            return Optional.of(rooms.get(doorPos));
        }
        return Optional.empty();
    }

    public void setDetectedRoom(Room room) {
        this.rooms.put(room.getDoorPos(), room);
    }

    public void unsetRoomAtDoorPos(Position doorPos) {
        this.rooms.remove(doorPos);
    }
}
