package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.*;
import java.util.stream.Stream;

public class TownState implements INBTSerializable<CompoundTag> {

    private final Quests activeQuests = new Quests();
    private final Map<Position, ResourceLocation> activeRecipes = new HashMap<>();
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
            ResourceLocation roomRecipe
    ) {
        this.activeRecipes.put(doorPos, roomRecipe);
        this.activeQuests.setStatus(roomRecipe, Quest.QuestStatus.COMPLETED);
    }

    public Optional<ResourceLocation> getRecipeAtDoorPos(Position doorPos) {
        if (activeRecipes.containsKey(doorPos)) {
            return Optional.of(activeRecipes.get(doorPos));
        }
        return Optional.empty();
    }

    public ResourceLocation unsetRecipeAtDoorPos(Position doorPos) {
        return activeRecipes.remove(doorPos);
    }

    public Quests getQuests() {
        return activeQuests;
    }

    public boolean hasRecipe(ResourceLocation quest) {
        return activeRecipes.containsValue(quest);
    }

    public boolean clearQuest(ResourceLocation quest) {
        // TODO: Instead of removing, mark as "done"
        //  That way, if the room is destroyed, the quest can be re-activated
        return activeQuests.setStatus(quest, Quest.QuestStatus.COMPLETED);
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
            this.activeQuests.add(r.getId());
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

    @Override
    public CompoundTag serializeNBT() {
        return null;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {

    }
}
