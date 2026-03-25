package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.*;
import java.util.function.Supplier;

public class QuestBatchSeed extends AbstractQuestGarden<MCQuestBatch, ResourceLocation> {

    private final ServerLevel level;
    private final int completedProceduralBatches;

    public MCQuestBatch get(
            MCReward rw,
            VillagerUUID owner
    ) {
        batch.setReward(rw);
        batch.assignTo(owner);
        return batch;
    }

    public QuestBatchSeed(
            ServerLevel level,
            UUID batchUUID,
            int targetItemWeight,
            int completedProceduralBatches
    ) {
        super(Config.IDEAL_QUEST_THRESHOLD_TICKS.get(), Config.QUEST_GENERATION_MAX_TICKS.get(), targetItemWeight);
        this.level = level;
        this.completedProceduralBatches = completedProceduralBatches;
        this.batch = new MCQuestBatch(batchUUID, null, null);
    }

    int targetItemWeight;
    int attempts = 0;

    @Override
    protected MCQuestBatch getEmptyBatch() {
        return new MCQuestBatch();
    }

    @Override
    protected ResourceLocation getRandomRoom(Collection<ResourceLocation> rooms) {
        return ImmutableList.copyOf(rooms).get(level.getRandom().nextInt(rooms.size()));
    }

    @Override
    protected boolean hasBedAlready(MCQuestBatch mcQuestBatch) {
        return mcQuestBatch.getAll().stream().anyMatch(v -> this.hasBeds(level, v));
    }

    private boolean hasBeds(
            ServerLevel level,
            MCQuest quest
    ) {
        Map<ResourceLocation, RoomRecipe> rr = RoomRecipes.hydrate(level.getRecipeManager(), true);
        NonNullList<Ingredient> roomIngredients = rr.get(quest.getWantedId()).getIngredients();
        return roomIngredients.stream().anyMatch(this::isBed);
    }

    private boolean isBed(Ingredient ing) {
        String tag = Ingredients.getTag(ing);
        if (tag != null) {
            return ItemTags.BEDS.location().equals(new ResourceLocation(tag));
        }
        // Otherwise, must be specific item. Check if it's a bed.
        return Arrays.stream(ing.getItems()).anyMatch(v -> v.is(ItemTags.BEDS));
    }

    @Override
    protected boolean questAlreadyRequested(
            MCQuestBatch mcQuestBatch,
            ResourceLocation id
    ) {
        return batch.getAll().stream().anyMatch(v -> id.equals(v.getWantedId()));
    }

    @Override
    protected int getBedCost() {
        return computeQuestCost(this::recipesFromLevel, SpecialQuests.BEDROOM, Integer.MAX_VALUE);
    }

    @Override
    protected int getCost(ResourceLocation randomRoom) {
        return computeQuestCost(this::recipesFromLevel, randomRoom, Integer.MAX_VALUE);
    }

    @Override
    protected void addQuest(
            MCQuestBatch mcQuestBatch,
            ResourceLocation next
    ) {
        mcQuestBatch.addNewQuest(null, next);
        if (completedProceduralBatches < 3) {
            List<MCQuest> all = mcQuestBatch.getAll();
            MCQuest quest = all.get(all.size() - 1);
            Component roomName = RoomRecipes.getName(next);
            quest.setFlavorText(String.format("Your village needs a %s. Build one to keep production flowing.", roomName.getString()));
        }
    }

    @Override
    protected void addBedQuest(
            UUID ownerUUID,
            MCQuestBatch mcQuestBatch
    ) {
        addQuest(mcQuestBatch, SpecialQuests.BEDROOM);
    }

    private Map<ResourceLocation, RoomRecipe> recipesFromLevel() {
        return RoomRecipes.hydrate(level.getRecipeManager(), false);
    }

    private static final Map<ResourceLocation, Integer> cachedCosts = new HashMap<>();

    public static int computeQuestCost(
            Supplier<Map<ResourceLocation, RoomRecipe>> recipes,
            ResourceLocation qID,
            int stopAt
    ) {
        if (cachedCosts.containsKey(qID)) {
            return cachedCosts.get(qID);
        }
        Map<ResourceLocation, RoomRecipe> hydrated = new HashMap<>(recipes.get());
        addBlockRooms(hydrated);
        if (!hydrated.containsKey(qID)) {
            throw new IllegalStateException("No recipe found for ID " + qID);
        }
        int recipeWeight = RoomRecipes.getRecipeWeight(hydrated.get(qID), stopAt);
        if (recipeWeight == 0) {
            QT.QUESTS_LOGGER.warn("Recipe weight is 0. This is probably a bug: {}", qID);
        }
        cachedCosts.put(qID, recipeWeight);
        return recipeWeight;
    }

    private static void addBlockRooms(Map<ResourceLocation, RoomRecipe> hydrated) {
        for (Supplier<RoomBlock> e : BlockAsRoomEntity.ALL) {
            RoomBlock roomBlock = e.get();
            if (roomBlock.asItem().equals(Items.AIR)) {
                String m = "Invalid initialization detected. RoomBlock's item seems unregistered: ";
                throw new IllegalStateException(m + roomBlock.getId());
            }
            ResourceLocation rID = RoomBlock.getRoomId(roomBlock);
            Ingredient ingr = Ingredient.of(roomBlock.asItem());
            hydrated.put(rID, new RoomRecipe(rID, NonNullList.withSize(1, ingr), 1, false));
        }
    }

    @Override
    protected boolean isOvergrown() {
        boolean isOvergrown = batch.size() > 50;
        if (isOvergrown) {
            QT.logBug("Quest batch overgrown: {}", batch);
            for (MCQuest q : batch.getAll()) {
                QT.QUESTS_LOGGER.debug(" - Quest: {} [Cost: {}]", q.getWantedId(),
                        computeQuestCost(this::recipesFromLevel, q.getWantedId(), Integer.MAX_VALUE));
            }
        }
        return isOvergrown;
    }

    @Override
    public String toString() {
        return "PendingQuests{" +
                "batch=" + batch +
                ", targetItemWeight=" + targetItemWeight +
                ", attempts=" + attempts +
                '}';
    }
}
