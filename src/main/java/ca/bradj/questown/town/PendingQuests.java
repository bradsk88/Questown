package ca.bradj.questown.town;

import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// TODO: Decouple from MC and test
public class PendingQuests {

    MCQuestBatch batch;

    public PendingQuests(int minItemWeight, UUID ownerUUID, MCReward reward) {
        this.maxItemWeight = minItemWeight;
        this.batch = new MCQuestBatch(ownerUUID, reward);
    }

    int maxItemWeight;
    int attempts = 0;

    public Optional<MCQuestBatch> grow(ServerLevel level) {
        attempts++;
        if (attempts > 50 && batch.size() > 0) { // TODO: Use config to set attempts
            return Optional.of(batch);
        }
        int cost = batch.getAll().stream()
                // TODO: Pre-compute and cache recipe costs
                .map(v -> PendingQuests.computeCosts(level, v.getId(), maxItemWeight))
                .reduce(Integer::sum)
                .orElse(0);
        if (cost >= maxItemWeight) {
            return Optional.of(batch);
        }
        if (attempts > 25 && cost > maxItemWeight * 0.5) { // TODO: Use config to set attempts
            return Optional.of(batch);
        }
        List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
        List<ResourceLocation> ids = recipes.stream().map(RoomRecipe::getId).toList();
        ResourceLocation id = ids.get(level.getRandom().nextInt(ids.size()));
        int newCost = computeCosts(level, id, maxItemWeight);
        if (attempts < 25 && (newCost < maxItemWeight / 4)) {
            // Ignore small rooms early on
            // TODO: compartmentalize pre-computed costs so we can grab expensive
            //  recipes first and then fill empty space with cheaper ones later.
            return Optional.empty();
        }
        if ((newCost > maxItemWeight / 2) || newCost > maxItemWeight - cost) {
            return Optional.empty();
        }
        batch.addNewQuest(id);
        return Optional.empty();
    }

    private static int computeCosts(ServerLevel level, ResourceLocation qID, int stopAt) {
        Map<ResourceLocation, RoomRecipe> hydrated = RoomRecipes.hydrate(level);
        if (!hydrated.containsKey(qID)) {
            throw new IllegalStateException("No recipe found for ID " + qID);
        }
        return RoomRecipes.getRecipeWeight(hydrated.get(qID), stopAt);
    }

}
