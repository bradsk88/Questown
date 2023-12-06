package ca.bradj.questown.logic;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.RecipeItemConfig;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.electronwill.nightconfig.core.Config;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        List<RoomRecipe> recipes = getAllRecipes(level);
        for (RoomRecipe r : recipes) {
            if (id.equals(r.getId())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    @NotNull
    public static List<RoomRecipe> getAllRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
    }

    public static Component getName(ResourceLocation id) {
        if (id.getPath().startsWith("special_quest")) {
            return new TranslatableContents(id.getPath());
        }
        return new TranslatableContents(String.format("room.%s", id.getPath()));
    }

    public static Component getName(Optional<ResourceLocation> recipe) {
        return recipe.map(RoomRecipes::getName)
                .orElseGet(() -> new TranslatableContents("room.no_recipe"));
    }

    public static Map<ResourceLocation, RoomRecipe> hydrate(ServerLevel sl) {
        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        sl.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        return rMapB.build();
    }

    public static int getRecipeWeight(RoomRecipe recipe, int stopAt) {
        Config allWeights = RecipeItemConfig.itemWeights.get();
        int weight = 0;
        for (Ingredient in : recipe.getIngredients()) {
            if (weight > stopAt) {
                return weight;
            }
            JsonElement js = in.toJson();
            boolean foundWeight = false;
            if (js.getAsJsonObject().has("item")) {
                String id = js.getAsJsonObject().get("item").getAsString();
                if (allWeights.contains(String.format("%s", id))) {
                    weight += allWeights.getInt(id);
                    foundWeight = true;
                } else {
                    QT.QUESTS_LOGGER.error(
                            "No weight specified for tag. Default of 100 will be used. [{}]", id
                    );
                }
            }
            if (js.getAsJsonObject().has("tag")) {
                String id = js.getAsJsonObject().get("tag").getAsString();
                String tID = String.format("#%s", id);
                if (allWeights.contains(tID)) {
                    weight += allWeights.getInt(tID);
                    foundWeight = true;
                } else {
                    QT.QUESTS_LOGGER.error(
                            "No weight specified for item. Default of 100 will be used. [{}]", id
                    );
                }
            }
            if (!foundWeight) {
                weight += ca.bradj.questown.core.Config.DEFAULT_ITEM_WEIGHT.get();
            }
        }
        return weight;
    }

    public static boolean containsAllTags(
            Iterable<? extends List<String>> haystack,
            Iterable<? extends List<String>> needles
    ) {
        boolean found = false;
        for (List<String> h : haystack) {
            boolean iFound = false;
            for (List<String> n : needles) {
                if (iFound) {
                    continue;
                }
                if (h.containsAll(n)) {
                    iFound = true;
                }
            }
            if (iFound) {
                found = true;
                continue;
            }
            return false;
        }
        return found;
    }
}

