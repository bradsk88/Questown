package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// TODO: Decouple from MC and test
public class QuestBatchSeed {

    private final AlreadyAddedChecker checker;
    MCQuestBatch batch;

    public MCQuestBatch get(
            MCReward rw,
            UUID owner
    ) {
        batch.setReward(rw);
        batch.assignTo(owner);
        return batch;
    }

    public interface AlreadyAddedChecker {
        boolean questAlreadyRequested(ResourceLocation questId);
    }

    public QuestBatchSeed(
            UUID batchUUID,
            AlreadyAddedChecker checker,
            int targetItemWeight
    ) {
        this.checker = checker;
        this.targetItemWeight = targetItemWeight;
        this.batch = new MCQuestBatch(batchUUID, null, null);
    }

    int targetItemWeight;
    int attempts = 0;
    boolean hasBed = false;

    // Returns true if the batch can still grow, or false if it has reached the
    // desired weight or exceeded a limit.
    public boolean grow(TownInterface town) {
        Integer maxTicks = Config.QUEST_GENERATION_MAX_TICKS.get();
        if (attempts >= maxTicks && batch.size() > 0) {
            return false;
        }

        if (!hasBed && !town.hasEnoughBeds()) {
            debugLog("Adding bed quest");
            batch.addNewQuest(null, SpecialQuests.BEDROOM);
            hasBed = true;
        }

        ServerLevel level = town.getServerLevel();
        if (level == null) {
            QT.QUESTS_LOGGER.error("Server level is null in PendingQuests");
            return false;
        }

        attempts++;
        int currentCost = computeCurrentCost(level);
        debugLog(
                "Current weight: {} of {} \n- {}",
                currentCost,
                targetItemWeight,
                String.join("\n- ", batch.getAll().stream().map(Quest::toShortString).toList())
        );
        ResourceLocation id = generateRandomQuest(level);
        int newCost = computeCosts(level, id, targetItemWeight);
        if (checker.questAlreadyRequested(id) || questAlreadyRequested(batch, id)) {
            newCost = (int) (newCost * Config.DUPLICATE_QUEST_COST_FACTOR.get());
        }
        int idealTicks = Config.IDEAL_QUEST_THRESHOLD_TICKS.get();
        debugLog("Iteration: {} of ideal {} max {}", attempts, idealTicks, maxTicks);
        debugLog("Trying to add {} [Cost: {}]", id, newCost);
        if (attempts < idealTicks && (newCost < targetItemWeight / 4)) {
            // Ignore small rooms early on
            // TODO: compartmentalize pre-computed costs so we can grab expensive
            //  recipes first and then fill empty space with cheaper ones later.
            debugLog("Ignoring {} for now. Looking for more interesting quests", id);
            return true;
        }
        if ((newCost > targetItemWeight / 2)) {
            debugLog("Room is more than 50% of target weight. Not adding {}", id);
            return true;
        }
        if (newCost > targetItemWeight - currentCost) {
            debugLog("Room would exceed weight limit {}. Not adding {}", targetItemWeight, id);
            return true;
        }
        debugLog("Successfully added to quest batch: {}", id);
        batch.addNewQuest(null, id);
        return true;
    }

    private void debugLog(String msg) {
        if (Config.LOG_QUEST_BATCH_GENERATION.get()) {
            QT.QUESTS_LOGGER.debug(msg);
        }
    }

    private void debugLog(
            String msg,
            Object p1
    ) {
        if (Config.LOG_QUEST_BATCH_GENERATION.get()) {
            QT.QUESTS_LOGGER.debug(msg, p1);
        }
    }

    private void debugLog(
            String msg,
            Object p1,
            Object p2
    ) {
        if (Config.LOG_QUEST_BATCH_GENERATION.get()) {
            QT.QUESTS_LOGGER.debug(msg, p1, p2);
        }
    }

    private void debugLog(
            String msg,
            Object p1,
            Object p2,
            Object p3
    ) {
        if (Config.LOG_QUEST_BATCH_GENERATION.get()) {
            QT.QUESTS_LOGGER.debug(msg, p1, p2, p3);
        }
    }

    private boolean questAlreadyRequested(MCQuestBatch batch, ResourceLocation id) {
        return batch.getAll().stream().anyMatch(v -> id.equals(v.getWantedId()));
    }

    private static ResourceLocation generateRandomQuest(ServerLevel level) {
        List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
        List<ResourceLocation> ids = recipes.stream().map(RoomRecipe::getId).toList();
        return ids.get(level.getRandom().nextInt(ids.size()));
    }

    @NotNull
    private Integer computeCurrentCost(ServerLevel level) {
        return batch.getAll().stream()
                .map(v -> QuestBatchSeed.computeCosts(level, v.getWantedId(), targetItemWeight))
                .reduce(Integer::sum)
                .orElse(0);
    }

    private static Map<ResourceLocation, Integer> cachedCosts = new HashMap<>();

    private static int computeCosts(
            ServerLevel level,
            ResourceLocation qID,
            int stopAt
    ) {
        if (cachedCosts.containsKey(qID)) {
            return cachedCosts.get(qID);
        }
        Map<ResourceLocation, RoomRecipe> hydrated = RoomRecipes.hydrate(level);
        if (!hydrated.containsKey(qID)) {
            throw new IllegalStateException("No recipe found for ID " + qID);
        }
        int recipeWeight = RoomRecipes.getRecipeWeight(hydrated.get(qID), stopAt);
        cachedCosts.put(qID, recipeWeight);
        return recipeWeight;
    }

    @Override
    public String toString() {
        return "PendingQuests{" +
                "batch=" + batch +
                ", targetItemWeight=" + targetItemWeight +
                ", attempts=" + attempts +
                ", hasBed=" + hasBed +
                '}';
    }
}
