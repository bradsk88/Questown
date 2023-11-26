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

import java.util.List;
import java.util.Map;
import java.util.UUID;

// TODO: Decouple from MC and test
public class PendingQuests {

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

    public PendingQuests(
            AlreadyAddedChecker checker,
            int targetItemWeight
    ) {
        this.checker = checker;
        this.targetItemWeight = targetItemWeight;
        this.batch = new MCQuestBatch(null, null); //
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
            QT.QUESTS_LOGGER.debug("Adding bed quest");
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
        QT.QUESTS_LOGGER.debug(
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
        QT.QUESTS_LOGGER.debug("Iteration: {} of ideal {} max {}", attempts, idealTicks, maxTicks);
        QT.QUESTS_LOGGER.debug("Trying to add {} [Cost: {}]", id, newCost);
        if (attempts < idealTicks && (newCost < targetItemWeight / 4)) {
            // Ignore small rooms early on
            // TODO: compartmentalize pre-computed costs so we can grab expensive
            //  recipes first and then fill empty space with cheaper ones later.
            QT.QUESTS_LOGGER.debug("Ignoring {} for now. Looking for more interesting quests", id);
            return true;
        }
        if ((newCost > targetItemWeight / 2)) {
            QT.QUESTS_LOGGER.debug("Room is more than 50% of target weight. Not adding {}", id);
            return true;
        }
        if (newCost > targetItemWeight - currentCost) {
            QT.QUESTS_LOGGER.debug("Room would exceed weight limit {}. Not adding {}", targetItemWeight, id);
            return true;
        }
        QT.QUESTS_LOGGER.debug("Successfully added to quest batch: {}", id);
        batch.addNewQuest(null, id);
        return true;
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
                // TODO: Pre-compute and cache recipe costs
                .map(v -> PendingQuests.computeCosts(level, v.getWantedId(), targetItemWeight))
                .reduce(Integer::sum)
                .orElse(0);
    }

    private static int computeCosts(
            ServerLevel level,
            ResourceLocation qID,
            int stopAt
    ) {
        Map<ResourceLocation, RoomRecipe> hydrated = RoomRecipes.hydrate(level);
        if (!hydrated.containsKey(qID)) {
            throw new IllegalStateException("No recipe found for ID " + qID);
        }
        return RoomRecipes.getRecipeWeight(hydrated.get(qID), stopAt);
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
