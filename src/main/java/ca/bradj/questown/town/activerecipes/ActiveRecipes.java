package ca.bradj.questown.town.activerecipes;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.core.Room;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// ActiveRecipes is a unit testable module for the active recipes of a town
public class ActiveRecipes<KEY> {

    protected final Map<Room, KEY> activeRecipes = new HashMap<>();

    // TODO: Support multiple?
    private ChangeListener<KEY> changeListener;

    public ActiveRecipes() {
    }

    void initialize(
            Set<Map.Entry<Room, KEY>> entries
    ) {
        if (activeRecipes.size() > 0) {
            throw new IllegalStateException("ActiveRecipes is already initialized");
        }
        entries.forEach(v -> activeRecipes.put(v.getKey(), v.getValue()));
    }

    public void update(
            Room room,
            Optional<KEY> recipe
    ) {
        Questown.LOGGER.trace("Updating recipe at " + room.getDoorPos() + " to " + recipe + " for room with space " + room.getSpace());

        if (recipe.isPresent()) {
            if (activeRecipes.containsKey(room)) {
                KEY oldRecipe = activeRecipes.get(room);
                if (oldRecipe.equals(recipe.get())) {
                    return;
                }
                this.activeRecipes.put(room, recipe.get());
                this.changeListener.roomRecipeChanged(room, oldRecipe, recipe.get());
                return;
            }

            this.activeRecipes.put(room, recipe.get());
            this.changeListener.roomRecipeCreated(room, recipe.get());
            return;
        }

        if (activeRecipes.containsKey(room)) {
            KEY oldRecipeId = activeRecipes.remove(room);
            this.changeListener.roomRecipeDestroyed(room, oldRecipeId);
            return;
        }

        if (recipe.isPresent()) {
            Questown.LOGGER.error("An unexpected recipe was removed. This is likely a bug.");
        }
    }

    public interface ChangeListener<KEY> {
        void roomRecipeCreated(Room room, KEY recipeId);
        void roomRecipeChanged(Room room, KEY oldRecipeId, KEY newRecipeId);
        void roomRecipeDestroyed(Room room, KEY oldRecipeId);
    }

    public void addChangeListener(ChangeListener<KEY> cl) {
        this.changeListener = cl;
    }

    public int size() {
        return activeRecipes.size();
    }
}
