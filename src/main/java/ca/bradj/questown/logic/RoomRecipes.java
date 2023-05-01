package ca.bradj.questown.logic;

import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RoomRecipes {

    public static Collection<Ingredient> filterSpecialBlocks(
            Iterable<Ingredient> ingredients
    ) {
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        boolean foundBed = false;
        for (Ingredient i : ingredients) {
            if (foundBed) {
                foundBed = false;
                continue;
            }
            if (i.getItems()[0].getItem() instanceof BedItem) {
                foundBed = true;
            }
            b.add(i);
        }
        return b.build();
    }
    public static Optional<RoomRecipe> getById(Level level, ResourceLocation id) {
        List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
        for (RoomRecipe r : recipes) {
            if (id.equals(r.getId())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    public static Component getName(ResourceLocation id) {
        return new TranslatableComponent(String.format("room.%s", id.getPath()));
    }
}

