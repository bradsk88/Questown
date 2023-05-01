package ca.bradj.questown.town.activerecipes;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

public class ActiveRecipesTest {

    private ActiveRecipes<Integer> activeRecipes;
    private ActiveRecipes.ChangeListener<Integer> listener;

    @BeforeEach
    public void setUp() {
        activeRecipes = new ActiveRecipes<>();
        listener = Mockito.mock(ActiveRecipes.ChangeListener.class);
        activeRecipes.addChangeListener(listener);
    }

    @Test
    public void testCreateRecipe() {
        Room room = new Room(new Position(0, 0), new InclusiveSpace(new Position(0, 0), new Position(1, 1)));
        int recipeId = 1;

        activeRecipes.update(room, Optional.of(recipeId));

        Mockito.verify(listener, Mockito.times(1)).roomRecipeCreated(room, recipeId);
    }

    @Test
    public void testChangeRecipe() {
        Room room = new Room(new Position(0, 0), new InclusiveSpace(new Position(0, 0), new Position(1, 1)));
        int oldRecipeId = 1;
        int newRecipeId = 2;

        activeRecipes.update(room, Optional.of(oldRecipeId));
        activeRecipes.update(room, Optional.of(newRecipeId));

        Mockito.verify(listener, Mockito.times(1)).roomRecipeCreated(room, oldRecipeId);
        Mockito.verify(listener, Mockito.times(1)).roomRecipeChanged(room, oldRecipeId, newRecipeId);
    }

    @Test
    public void testUnchangedRecipe() {
        Room room = new Room(new Position(0, 0), new InclusiveSpace(new Position(0, 0), new Position(1, 1)));
        int sameRecipeId = 1;

        activeRecipes.update(room, Optional.of(sameRecipeId));
        activeRecipes.update(room, Optional.of(sameRecipeId));

        Mockito.verify(listener, Mockito.times(1)).roomRecipeCreated(room, sameRecipeId);
        Mockito.verify(listener, Mockito.never()).roomRecipeChanged(Mockito.any(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(listener, Mockito.never()).roomRecipeDestroyed(Mockito.any(), Mockito.anyInt());
    }

    @Test
    public void testDestroyRecipe() {
        Room room = new Room(new Position(0, 0), new InclusiveSpace(new Position(0, 0), new Position(1, 1)));
        int recipeId = 1;

        activeRecipes.update(room, Optional.of(recipeId));
        activeRecipes.update(room, Optional.empty());

        Mockito.verify(listener, Mockito.times(1)).roomRecipeCreated(room, recipeId);
        Mockito.verify(listener, Mockito.times(1)).roomRecipeDestroyed(room, recipeId);
    }

    @Test
    public void testDestroyNonexistentRecipe() {
        Room room = new Room(new Position(0, 0), new InclusiveSpace(new Position(0, 0), new Position(1, 1)));
        int recipeId = 1;

        activeRecipes.update(room, Optional.empty());
        activeRecipes.update(room, Optional.empty());

        Mockito.verify(listener, Mockito.never()).roomRecipeCreated(room, recipeId);
        Mockito.verify(listener, Mockito.never()).roomRecipeDestroyed(room, recipeId);
    }
}
