package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.ChangeJobReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class UIQuest implements Comparable<UIQuest> {

    public static final UIQuest BROKEN = new UIQuest(
            null,
            SpecialQuests.BROKEN,
            Quest.QuestType.UNKNOWN,
            null,
            Quest.QuestStatus.ACTIVE,
            null,
            null,
            null
    );
    public final Quest.QuestStatus status;
    private final ResourceLocation wantedId;
    private final Quest.QuestType type;
    private final ImmutableList<Ingredient> ingredients;
    public final ResourceLocation fromRecipe;
    private final VillagerUUID villagerUUID;
    private final UUID batchUUID;
    private final String jobName;
    public final boolean isBroken;

    public UIQuest(
            UUID batchUUID,
            ResourceLocation wantedId,
            Quest.QuestType type,
            @Nullable Collection<Ingredient> ingredients,
            Quest.QuestStatus status,
            @Nullable ResourceLocation fromRecipe,
            @Nullable VillagerUUID jobRecipientUUID,
            @Nullable String jobName
    ) {
        this.isBroken = SpecialQuests.BROKEN.equals(wantedId) || ingredients == null;
        this.wantedId = wantedId;
        this.type = type;
        this.ingredients = ingredients == null ? ImmutableList.of() : ImmutableList.copyOf(ingredients);
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
        BlockAsRoomEntity.ALL.forEach(v -> {
            RoomBlock vv = v.get();
            rMapB.put(RoomBlock.getRoomId(vv), vv.asRecipe());
        });
        level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.getId(), v));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = rMapB.build();

        return aQ.stream().map(z -> {
            Quest<ResourceLocation, MCRoom> v = z.getKey();

            @Nullable String job = null;
            MCReward value = z.getValue();
            if (value instanceof ChangeJobReward cjr) {
                job = cjr.getJobName();
            }
            if (job == null) {
                job = findJob(value);
            }

            VillagerUUID jobRecipientUUID = null;
            if (v.getUUID() != null) {
                jobRecipientUUID = v.getUUID();
            }
            Collection<Ingredient> ingredientz = getIngredients(v, rMap);
            return new UIQuest(
                    v.getBatchUUID(),
                    v.getWantedId(),
                    v.getType(),
                    ingredientz,
                    v.getStatus(),
                    v.fromRecipeID().orElse(null),
                    jobRecipientUUID,
                    job
            );
        }).toList();
    }

    private static Collection<Ingredient> getIngredients(
            Quest<ResourceLocation, MCRoom> v,
            ImmutableMap<ResourceLocation, RoomRecipe> rMap
    ) {
        IForgeRegistry<Item> reg = ForgeRegistries.ITEMS;
        return switch (v.getType()) {
            case ROOM -> getRoomIngredients(v, rMap);
            case ITEM -> Collections.nCopies(v.getCountNeeded(), Ingredient.of(reg.getValue(v.getWantedId())));
            case JOB_CHANGE -> ImmutableList.of(Ingredient.of(ItemsInit.BLOCK_OF_PROGRESS.get()));
            case UNKNOWN -> ImmutableList.of();
        };
    }

    private static Collection<Ingredient> getRoomIngredients(
            Quest<ResourceLocation, MCRoom> v,
            ImmutableMap<ResourceLocation, RoomRecipe> rMap
    ) {
        RoomRecipe recip = rMap.get(v.getWantedId());
        if (recip == null) {
            Questown.LOGGER.warn("No recipe found for quest: " + v.getWantedId());
            return ImmutableList.of();
        }
        return recip.getIngredients();
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
        return type.compareTo(uiQuest.type); // TODO: Consider bringing back quest sorting in UI
    }

    public ResourceLocation getWantedId() {
        return wantedId;
    }

    public Collection<Ingredient> getIngredients() {
        return ingredients;
    }

    public Component getName() {
        if (SpecialQuests.isSpecialQuest(wantedId)) {
            return Compat.translatable(wantedId.getPath());
        }
        return switch (type) {
            case ROOM -> RoomRecipes.getName(wantedId);
            case ITEM -> Compat.translatable("menu.common.quantity", Compat.getItemName(wantedId), ingredients.size());
            case JOB_CHANGE -> Compat.translatable("questown.menu.quests.job_change", extractJobName());
            case UNKNOWN -> Compat.literal("ERROR");
        };
    }

    private String extractJobName() {
        return JobID.fromRL(wantedId).jobId();
    }

    public String jobName() {
        return jobName;
    }

    public String villagerUUID() {
        return VillagerUUID.getStringUUID(villagerUUID);
    }

    public UUID getBatchUUID() {
        return batchUUID;
    }

    public Quest.QuestType getType() {
        return type;
    }

    public static class Serializer {

        public void toNetwork(
                FriendlyByteBuf buf,
                UIQuest p_44102_
        ) {
            buf.writeUtf(p_44102_.status.asString());
            buf.writeResourceLocation(p_44102_.wantedId);
            QuestTypes.toNetwork(buf, p_44102_.type);
            buf.writeCollection(p_44102_.ingredients, (b, i) -> Ingredients.toNetwork(i, b));
            String fromStr = "";
            if (p_44102_.fromRecipe != null) {
                fromStr = p_44102_.fromRecipe.toString();
            }
            buf.writeUtf(fromStr);
            VillagerUUID.toNetwork(buf, p_44102_.villagerUUID);
            String jobName = "";
            if (p_44102_.jobName != null) {
                jobName = p_44102_.jobName;
            }
            buf.writeUtf(jobName);
            buf.writeUtf(p_44102_.batchUUID == null ? "" : p_44102_.batchUUID.toString());
        }

        @Nullable
        public UIQuest fromNetwork(
                ResourceLocation p_44105_,
                FriendlyByteBuf buf
        ) {
            Quest.QuestStatus status = Quest.QuestStatus.fromString(buf.readUtf());
            ResourceLocation wanteId = buf.readResourceLocation();
            Quest.QuestType type = QuestTypes.fromNetwork(buf);
            Collection<Ingredient> ingrs = buf.readList(Ingredients::fromNetwork);
            String fromStr = buf.readUtf();
            ResourceLocation from = null;
            if (!fromStr.isEmpty()) {
                from = new ResourceLocation(fromStr);
            }
            VillagerUUID villagerUUID = VillagerUUID.fromNetwork(buf);
            String jobName = buf.readUtf();
            String maybeBatchUUID = buf.readUtf();
            UUID batchUUID = null;
            if (!maybeBatchUUID.isEmpty()) {
                batchUUID = UUID.fromString(maybeBatchUUID);
            }
            return new UIQuest(batchUUID, wanteId, type, ingrs, status, from, villagerUUID, jobName);
        }
    }
}
