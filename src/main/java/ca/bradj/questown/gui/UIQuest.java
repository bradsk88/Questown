package ca.bradj.questown.gui;

import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.serialization.MCRoom;
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
    public final ResourceLocation fromRecipe;

    public UIQuest(
            RoomRecipe recipe,
            Quest.QuestStatus status,
            @Nullable ResourceLocation fromRecipe
    ) {
        this.recipe = recipe;
        this.status = status;
        this.fromRecipe = fromRecipe;
    }

    public static List<UIQuest> fromLevel(
            Level level,
            Collection<? extends Quest<ResourceLocation, MCRoom>> aQ
    ) {
        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        return aQ.stream().map(v -> {
            RoomRecipe q = rMap.get(v.getWantedId());
            if (q == null) {
                return null;
            }
            int recipeStrength = 1; // TODO: Add getter to RoomRecipes
            return new UIQuest(
                    new RoomRecipe(v.getWantedId(), q.getIngredients(), recipeStrength),
                    v.getStatus(), v.fromRecipeID().orElse(null)
            );
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
            ResourceLocation fromID = null;
            if (p_44104_.has("from_id")) {
                fromID = new ResourceLocation(p_44104_.get("from_id").getAsString());
            }
            return new UIQuest(recipe, Quest.QuestStatus.valueOf(status), fromID);
        }

        public void toNetwork(
                FriendlyByteBuf buf,
                UIQuest p_44102_
        ) {
            buf.writeUtf(p_44102_.status.asString());
            this.recipeSerializer.toNetwork(buf, p_44102_.recipe);
            String fromStr = "";
            if (p_44102_.fromRecipe != null) {
                fromStr = p_44102_.fromRecipe.toString();
            }
            buf.writeUtf(fromStr);
        }

        @Nullable
        public UIQuest fromNetwork(
                ResourceLocation p_44105_,
                FriendlyByteBuf buf
        ) {
            String status = buf.readUtf();
            RoomRecipe rec = this.recipeSerializer.fromNetwork(p_44105_, buf);
            String fromStr = buf.readUtf();
            ResourceLocation from = null;
            if (!fromStr.isEmpty()) {
                from = new ResourceLocation(fromStr);
            }
            return new UIQuest(rec, Quest.QuestStatus.fromString(status), from);
        }
    }
}
