package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.ChangeJobReward;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UIQuest implements Comparable<UIQuest> {

    private static final String NBT_VILLAGER_UUID = "villager_uuid";
    private static final String NBT_BATCH_UUID = "batch_uuid";
    private static final String NBT_JOB_NAME = "job_name";

    public final Quest.QuestStatus status;
    private final RoomRecipe recipe;
    public final ResourceLocation fromRecipe;
    private final String villagerUUID;
    private final UUID batchUUID;
    private final String jobName;

    public UIQuest(
            UUID batchUUID,
            RoomRecipe recipe,
            Quest.QuestStatus status,
            @Nullable ResourceLocation fromRecipe,
            @Nullable String jobRecipientUUID,
            @Nullable String jobName
    ) {
        this.recipe = recipe;
        this.status = status;
        this.fromRecipe = fromRecipe;
        this.villagerUUID = jobRecipientUUID;
        this.jobName = jobName;
        this.batchUUID = batchUUID;
    }

    public static List<UIQuest> fromLevel(
            ServerLevel level,
            QuestBatch<ResourceLocation, MCRoom, MCQuest, MCReward> batch
    ) {
        ImmutableMap.Builder<Quest<ResourceLocation, MCRoom>, MCReward> b = ImmutableMap.builder();
        batch.getAll().forEach(v -> b.put(v, batch.getReward()));
        return fromLevel(level, b.build().entrySet());
    }

    public static List<UIQuest> fromLevel(
            Level level,
            Collection<? extends Map.Entry<? extends Quest<ResourceLocation, MCRoom>, MCReward>> aQ
    ) {
        ImmutableMap.Builder<ResourceLocation, RoomRecipe> rMapB = ImmutableMap.builder();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        return aQ.stream().map(z -> {
            Quest<ResourceLocation, MCRoom> v = z.getKey();
            RoomRecipe q = rMap.get(v.getWantedId());
            if (q == null) {
                return null;
            }
            int recipeStrength = 1; // TODO: Add getter to RoomRecipes

            @Nullable String job = null;
            MCReward value = z.getValue();
            if (value instanceof ChangeJobReward cjr) {
                job = cjr.getJobName();
            }
            if (job == null) {
                job = findJob(value);
            }

            String jobRecipientUUID = null;
            if (v.getUUID() != null) {
                jobRecipientUUID = v.getUUID().toString();
            }
            return new UIQuest(
                    v.getBatchUUID(),
                    new RoomRecipe(v.getWantedId(), q.getIngredients(), recipeStrength),
                    v.getStatus(), v.fromRecipeID().orElse(null),
                    jobRecipientUUID, job
            );
        }).toList();
    }

    @Nullable
    private static String findJob(
            MCReward value
    ) {
        String job = null;
        if (value instanceof ChangeJobReward cjr) {
            return cjr.getJobName();
        }
        if (value instanceof MCRewardContainer mrc) {
            for (MCReward r : mrc.getContainedRewards()) {
                @Nullable String j = findJob(r);
                if (j != null) {
                    if (job != null) {
                        Questown.LOGGER.warn("Multiple job change rewards in a single villager quest batch.");
                    }
                    job = j;
                }
            }
        }
        return job;
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

    public String jobName() {
        return jobName;
    }

    public String villagerUUID() {
        return villagerUUID;
    }

    public UUID getBatchUUID() {
        return batchUUID;
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
            String villagerUUID = null;
            if (p_44104_.has(NBT_VILLAGER_UUID)) {
                villagerUUID = p_44104_.get(NBT_VILLAGER_UUID).getAsString();
            }
            String batchUUID = null;
            if (p_44104_.has(NBT_BATCH_UUID)) {
                batchUUID = p_44104_.get(NBT_BATCH_UUID).getAsString();
            }
            String jobName = null;
            if (p_44104_.has(NBT_JOB_NAME)) {
                jobName = p_44104_.get(NBT_JOB_NAME).getAsString();
            }
            return new UIQuest(UUID.fromString(batchUUID), recipe, Quest.QuestStatus.valueOf(status), fromID, villagerUUID, jobName);
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
            String villagerUUID = "";
            if (p_44102_.villagerUUID != null) {
                villagerUUID = p_44102_.villagerUUID.toString();
            }
            buf.writeUtf(villagerUUID);
            String jobName = "";
            if (p_44102_.jobName != null) {
                jobName = p_44102_.jobName.toString();
            }
            buf.writeUtf(jobName);
            buf.writeUtf(p_44102_.batchUUID == null ? "" : p_44102_.batchUUID.toString());
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
            String villagerUUID = buf.readUtf();
            String jobName = buf.readUtf();
            String maybeBatchUUID = buf.readUtf();
            UUID batchUUID = null;
            if (!maybeBatchUUID.isEmpty()) {
                batchUUID = UUID.fromString(maybeBatchUUID);
            }
            return new UIQuest(batchUUID, rec, Quest.QuestStatus.fromString(status), from, villagerUUID, jobName);
        }
    }
}
