package ca.bradj.questown.logic;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.RecipeItemConfig;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.RecipeRef;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
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

    public static Optional<RoomRecipe> getById(
            Level level,
            ResourceLocation id
    ) {
        // The holder owns the id in 1.20.2+; the codec-constructed RoomRecipe carries a null id.
        return RecipeRef.recipeById(level.getRecipeManager(), RecipesInit.ROOM, id);
    }

    @NotNull
    public static List<RoomRecipe> getAllRecipes(Level level) {
        return RecipeRef.recipesOf(level.getRecipeManager(), RecipesInit.ROOM);
    }

    public static Component getName(ResourceLocation id) {
        return Compat.translatable(String.format("room.%s", id.getPath()));
    }

    public static Component getName(Optional<ResourceLocation> recipe) {
        return recipe.map(RoomRecipes::getName)
                     .orElseGet(() -> Compat.translatable("room.no_recipe"));
    }

    public static Map<ResourceLocation, RoomRecipe> hydrate(
            RecipeManager recipes,
            boolean includeSpecial
    ) {
        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        if (includeSpecial) {
            SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        } else {
            rMapB.put(SpecialQuests.FARM, SpecialQuests.SPECIAL_QUESTS.get(SpecialQuests.FARM));
        }
        // The holder owns the id in 1.20.2+; the codec-constructed RoomRecipe carries a null id.
        recipes.getAllRecipesFor(RecipesInit.ROOM).forEach(h -> rMapB.put(h.id(), h.value()));
        return rMapB.build();
    }

    public static int getRecipeWeight(
            RoomRecipe recipe,
            int stopAt
    ) {
        Map<String, Integer> allWeights = RecipeItemConfig.getWeights();
        int weight = 0;
        for (Ingredient in : recipe.getIngredients()) {
            if (weight > stopAt) {
                return weight;
            }
            boolean foundWeight = false;
            if (getAsJsonObject(in).has("item")) {
                String id = getAsJsonObject(in).get("item").getAsString();
                Integer itemWeight = allWeights.get(id);
                if (itemWeight != null) {
                    weight += itemWeight;
                    foundWeight = true;
                } else {
                    QT.QUESTS_LOGGER.error(
                            "No weight specified for item. Default of 100 will be used. [{}]", id
                    );
                }
            }
            if (getAsJsonObject(in).has("tag")) {
                String id = getAsJsonObject(in).get("tag").getAsString();
                String tID = String.format("#%s", id);
                Integer tagWeight = allWeights.get(tID);
                if (tagWeight != null) {
                    weight += tagWeight;
                    foundWeight = true;
                } else {
                    QT.QUESTS_LOGGER.error(
                            "No weight specified for tag. Default of 100 will be used. [{}]", id
                    );
                }
            }
            if (!foundWeight) {
                weight += Compat.configGet(ca.bradj.questown.core.Config.DEFAULT_ITEM_WEIGHT).get();
            }
        }
        return weight;
    }

    private static JsonObject getAsJsonObject(Ingredient js) {
        try {
            com.google.gson.JsonElement j = Ingredient.CODEC_NONEMPTY
                    .encode(js, com.mojang.serialization.JsonOps.INSTANCE, com.mojang.serialization.JsonOps.INSTANCE.empty())
                    .getOrThrow();
            return j.getAsJsonObject();
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Not a JSON object: " + js, e);
        }
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

    public static RoomRecipe standard(
            @NotNull ResourceLocation roomId,
            NonNullList<Ingredient> of
    ) {
        return new RoomRecipe(roomId, of, 1, false);
    }
}

