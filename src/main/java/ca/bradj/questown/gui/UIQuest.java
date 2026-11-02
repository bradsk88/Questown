package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Jobs;
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
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
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
            null,
            null,
            null
    );
    public final Quest.QuestStatus status;
    private final ResourceLocation wantedId;


    /** A LOOT quest's name: its tag's name, or the wanted item's own name. */
    public static MutableComponent lootName(ResourceLocation wantedId) {
        String key = LootQuestNames.key(wantedId);
        if (key == null) {
            return Compat.getItemName(wantedId).copy();
        }
        return Compat.translatable(key);
    }
    private final Quest.QuestType type;
    private final ImmutableList<Ingredient> ingredients;
    public final ResourceLocation fromRecipe;
    private final VillagerUUID villagerUUID;
    private final UUID batchUUID;
    private final String jobName;
    @Nullable
    private final String flavorText;
    /** Progress toward the quest's count (stored items for ITEM, items brought back for LOOT), or null. */
    @Nullable
    private final Integer deposited;
    /** The villager level a skill quest's card needs before it shows the skill, or null. */
    @Nullable
    private final Integer lockedUntilLevel;
    public final boolean isBroken;

    public UIQuest(
            UUID batchUUID,
            ResourceLocation wantedId,
            Quest.QuestType type,
            @Nullable Collection<Ingredient> ingredients,
            Quest.QuestStatus status,
            @Nullable ResourceLocation fromRecipe,
            @Nullable VillagerUUID jobRecipientUUID,
            @Nullable String jobName,
            @Nullable String flavorText,
            @Nullable Integer deposited
    ) {
        this(batchUUID, wantedId, type, ingredients, status, fromRecipe, jobRecipientUUID, jobName, flavorText,
                deposited, null);
    }

    public UIQuest(
            UUID batchUUID,
            ResourceLocation wantedId,
            Quest.QuestType type,
            @Nullable Collection<Ingredient> ingredients,
            Quest.QuestStatus status,
            @Nullable ResourceLocation fromRecipe,
            @Nullable VillagerUUID jobRecipientUUID,
            @Nullable String jobName,
            @Nullable String flavorText,
            @Nullable Integer deposited,
            @Nullable Integer lockedUntilLevel
    ) {
        this.lockedUntilLevel = lockedUntilLevel;
        this.isBroken = SpecialQuests.BROKEN.equals(wantedId) || ingredients == null;
        this.wantedId = wantedId;
        this.type = type;
        this.ingredients = ingredients == null ? ImmutableList.of() : ImmutableList.copyOf(ingredients);
        this.status = status;
        this.fromRecipe = fromRecipe;
        this.villagerUUID = jobRecipientUUID;
        this.jobName = jobName;
        this.flavorText = flavorText;
        this.batchUUID = batchUUID;
        this.deposited = deposited;
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
        return fromLevel(level, aQ, java.util.Map.of());
    }

    /** As above, with each open ITEM or LOOT quest's progress from {@code questProgress}. */
    public static List<UIQuest> fromLevel(
            Level level,
            Collection<? extends Map.Entry<? extends Quest<ResourceLocation, MCRoom>, MCReward>> aQ,
            Map<ResourceLocation, Integer> questProgress
    ) {
        return fromLevel(level, aQ, questProgress, java.util.Map.of());
    }

    /** As above, with the quests in {@code lockedSkillLevels} shown as blank skill cards. */
    public static List<UIQuest> fromLevel(
            Level level,
            Collection<? extends Map.Entry<? extends Quest<ResourceLocation, MCRoom>, MCReward>> aQ,
            Map<ResourceLocation, Integer> questProgress,
            Map<ResourceLocation, Integer> lockedSkillLevels
    ) {
        java.util.Map<ResourceLocation, RoomRecipe> rMapB = new java.util.LinkedHashMap<>();
        SpecialQuests.SPECIAL_QUESTS.forEach(rMapB::put);
        BlockAsRoomEntity.ALL.forEach(v -> {
            RoomBlock vv = v.get();
            rMapB.put(RoomBlock.getRoomId(vv), vv.asRecipe());
        });
        level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).forEach(v -> rMapB.put(v.id(), v.value()));
        // The job board quest's card shows the job board itself, not the sign in its room recipe.
        rMapB.put(SpecialQuests.JOB_BOARD, RoomRecipes.standard(
                SpecialQuests.JOB_BOARD,
                NonNullList.withSize(1, Ingredient.of(ItemsInit.JOB_BOARD_BLOCK.get()))
        ));
        ImmutableMap<ResourceLocation, RoomRecipe> rMap = ImmutableMap.copyOf(rMapB);

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
                    removableBatch(v),
                    v.getWantedId(),
                    v.getType(),
                    ingredientz,
                    v.getStatus(),
                    v.fromRecipeID().orElse(null),
                    jobRecipientUUID,
                    job,
                    v.getFlavorText(),
                    QuestCardProgress.shown(v.getType(), v.getStatus(), v.getCountNeeded(), questProgress.get(v.getWantedId())),
                    v.isComplete() ? null : lockedSkillLevels.get(v.getWantedId())
            );
        }).toList();
    }

    private static Collection<Ingredient> getIngredients(
            Quest<ResourceLocation, MCRoom> v,
            ImmutableMap<ResourceLocation, RoomRecipe> rMap
    ) {
        return switch (v.getType()) {
            case ROOM -> getRoomIngredients(v, rMap);
            case ITEM -> Collections.nCopies(v.getCountNeeded(), Ingredient.of(Loader.itemFromId(v.getWantedId())));
            case LOOT -> Collections.nCopies(v.getCountNeeded(), Ingredients.asIngredient(v.getWantedId()));
            case JOB_CHANGE -> ImmutableList.of(Ingredient.of(ItemsInit.BLOCK_OF_PROGRESS.get()));
            case JOB_UNLOCK -> ImmutableList.of(Ingredient.of(ItemsInit.BLOCK_OF_PROGRESS.get()));
            case CONCURRENT_JOBS -> ImmutableList.of(Ingredient.of(ItemsInit.BLOCK_OF_PROGRESS.get()));
            case FIRST_WORK_REQUEST -> ImmutableList.of();
            case SKILL_UNLOCK -> ImmutableList.of(Ingredient.of(ItemsInit.BLOCK_OF_PROGRESS.get()));
            case VILLAGER_LEVEL -> ImmutableList.of();
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
        return switch (type) {
            case ROOM -> RoomRecipes.getName(wantedId);
            case ITEM -> {
                MutableComponent name = Compat.translatable("menu.common.quantity", Compat.getItemName(wantedId), ingredients.size());
                if (deposited != null) {
                    name.append(Compat.literal(" (" + Math.min(deposited, ingredients.size()) + "/" + ingredients.size() + ")"));
                }
                yield name;
            }
            case LOOT -> {
                MutableComponent name = lootName(wantedId);
                if (deposited != null) {
                    name.append(Compat.literal(" (" + Math.min(deposited, ingredients.size()) + "/" + ingredients.size() + ")"));
                }
                yield name;
            }
            case JOB_CHANGE -> Compat.translatable("questown.menu.quests.job_change", extractRootJobName());
            case JOB_UNLOCK -> Compat.translatable("questown.menu.quests.job_unlock", extractRootJobName());
            case CONCURRENT_JOBS -> Compat.literal("Multiple Jobs Active");
            case FIRST_WORK_REQUEST -> Compat.translatable("menu.quests.first_work_request");
            case SKILL_UNLOCK -> lockedUntilLevel != null
                    ? Compat.translatable("menu.quests.skill_unlock.hidden")
                    : MCQuest.namedSkill(wantedId)
                    .map(s -> Compat.translatable("menu.quests.skill_unlock.named", Jobs.getVariantNameComponent(s)))
                    .orElseGet(() -> Compat.translatable("menu.quests.skill_unlock",
                            Compat.translatable("jobs." + MCQuest.skillTreeRoot(wantedId))));
            case VILLAGER_LEVEL -> villagerLevelName(wantedId, deposited);
            case UNKNOWN -> Compat.literal("ERROR");
        };
    }

    /** "Train a Crafter to level 3 (1/3)": the progress is the job's highest villager level. */
    public static MutableComponent villagerLevelName(ResourceLocation wantedId, @Nullable Integer highestLevel) {
        Optional<VillagerLevelQuests.Target> t = VillagerLevelQuests.target(wantedId);
        if (t.isEmpty()) {
            return Compat.literal("ERROR").copy();
        }
        MutableComponent name = Compat.translatable("menu.quests.villager_level",
                Compat.translatable("jobs." + t.get().root()), t.get().level());
        if (highestLevel != null) {
            name.append(Compat.literal(" (" + Math.min(highestLevel, t.get().level()) + "/" + t.get().level() + ")"));
        }
        return name;
    }

    public @Nullable Integer getLockedUntilLevel() {
        return lockedUntilLevel;
    }

    @Nullable
    public String getFlavorText() {
        return flavorText;
    }

    private Component extractRootJobName() {
        return Compat.translatable("jobs." + JobID.fromRL(wantedId).rootId());
    }

    public String jobName() {
        return jobName;
    }

    public String villagerUUID() {
        return VillagerUUID.getStringUUID(villagerUUID);
    }

    /** The batch the card's remove button discards, or null when the quest can't be removed. */
    public static @Nullable UUID removableBatch(Quest<?, ?> quest) {
        return quest.isDiscardable() ? quest.getBatchUUID() : null;
    }

    public UUID getBatchUUID() {
        return batchUUID;
    }

    public Quest.QuestType getType() {
        return type;
    }

    @Nullable
    public Integer getDeposited() {
        return deposited;
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
            buf.writeUtf(p_44102_.flavorText == null ? "" : p_44102_.flavorText);
            buf.writeInt(p_44102_.deposited == null ? -1 : p_44102_.deposited);
            buf.writeInt(p_44102_.lockedUntilLevel == null ? -1 : p_44102_.lockedUntilLevel);
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
            String flavorText = buf.readUtf();
            if (flavorText.isEmpty()) {
                flavorText = null;
            }
            int deposited = buf.readInt();
            int locked = buf.readInt();
            return new UIQuest(batchUUID, wanteId, type, ingrs, status, from, villagerUUID, jobName, flavorText,
                    deposited < 0 ? null : deposited, locked < 0 ? null : locked);
        }
    }
}
