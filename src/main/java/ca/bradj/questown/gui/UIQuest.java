package ca.bradj.questown.gui;

import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class UIQuest implements Comparable<UIQuest> {

    public final Quest.QuestStatus status;
    private final RoomRecipe recipe;

    public UIQuest(
            RoomRecipe recipe,
            Quest.QuestStatus status
    ) {
        this.recipe = recipe;
        this.status = status;
    }

    public static List<UIQuest> fromLevel(
            Level level,
            Collection<? extends Quest<ResourceLocation>> aQ
    ) {
        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        return aQ.stream().map(v -> {
            RoomRecipe q = rMap.get(v.getId());
            if (q == null) {
                return null;
            }
            int recipeStrength = 1; // TODO: Add getter to RoomRecipes
            return new UIQuest(new RoomRecipe(v.getId(), q.getIngredients(), recipeStrength), v.getStatus());
        }).toList();
    }

    @Override
    public int compareTo(@NotNull UIQuest uiQuest) {
        int sComp = status.compareTo(uiQuest.status);
        if (sComp != 0) {
            return sComp;
        }
        return recipe.compareTo(uiQuest.recipe);
    }

    public ResourceLocation getRecipeId() {
        return recipe.getId();
    }

    public Collection<Ingredient> getIngredients() {
        return recipe.getIngredients();
    }

    public Component getName() {
        if (SpecialQuests.isSpecialQuest(recipe.getId())) {
            return new TranslatableComponent(recipe.getId().getPath());
        }
        return RoomRecipes.getName(recipe.getId());
    }

    public static class Serializer {

        private final RoomRecipe.Serializer recipeSerializer;

        public Serializer() {
            this.recipeSerializer = new RoomRecipe.Serializer();
        }

        public UIQuest fromJson(
                ResourceLocation p_44103_,
                JsonObject p_44104_
        ) {
            String status = p_44104_.get("status").getAsString();
            @NotNull RoomRecipe recipe = this.recipeSerializer.fromJson(
                    p_44103_,
                    p_44104_.getAsJsonObject("recipe")
            );
            return new UIQuest(recipe, Quest.QuestStatus.valueOf(status));
        }

        @Nullable
        public UIQuest fromNetwork(
                ResourceLocation p_44105_,
                FriendlyByteBuf p_44106_
        ) {
            String status = p_44106_.readUtf();
            RoomRecipe rec = this.recipeSerializer.fromNetwork(p_44105_, p_44106_);
            return new UIQuest(rec, Quest.QuestStatus.fromString(status));
        }

        public void toNetwork(
                FriendlyByteBuf p_44101_,
                UIQuest p_44102_
        ) {
            p_44101_.writeUtf(p_44102_.status.asString());
            this.recipeSerializer.toNetwork(p_44101_, p_44102_.recipe);
        }
    }
}
