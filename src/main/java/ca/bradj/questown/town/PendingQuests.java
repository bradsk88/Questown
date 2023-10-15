package ca.bradj.questown.town;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// TODO: Decouple from MC and test
public class PendingQuests {

    MCQuestBatch batch;

    public PendingQuests(int targetItemWeight, @Nullable UUID ownerUUID, MCReward reward) {
        this.targetItemWeight = targetItemWeight;
        this.batch = new MCQuestBatch(ownerUUID, reward);
    }

    int targetItemWeight;
    int attempts = 0;
    boolean hasBed = false;

    public Optional<MCQuestBatch> grow(TownInterface town) {
        if (!hasBed && !town.hasEnoughBeds()) {
            batch.addNewQuest(null, SpecialQuests.BEDROOM);
            hasBed = true;
        }

        ServerLevel level = town.getServerLevel();
        attempts++;
        if (attempts > Config.QUEST_GENERATION_MAX_TICKS.get() && batch.size() > 0) {
            return Optional.of(batch);
        }
        int cost = batch.getAll().stream()
                // TODO: Pre-compute and cache recipe costs
                .map(v -> PendingQuests.computeCosts(level, v.getWantedId(), targetItemWeight))
                .reduce(Integer::sum)
                .orElse(0);
        if (cost >= targetItemWeight) {
            return Optional.of(batch);
        }
        int idealTicks = Config.IDEAL_QUEST_THRESHOLD_TICKS.get();
        if (attempts > idealTicks && cost > targetItemWeight * 0.5) {
            return Optional.of(batch);
        }
        List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM);
        List<ResourceLocation> ids = recipes.stream().map(RoomRecipe::getId).toList();
        ResourceLocation id = ids.get(level.getRandom().nextInt(ids.size()));
        int newCost = computeCosts(level, id, targetItemWeight);
        if (attempts < idealTicks && (newCost < targetItemWeight / 4)) {
            // Ignore small rooms early on
            // TODO: compartmentalize pre-computed costs so we can grab expensive
            //  recipes first and then fill empty space with cheaper ones later.
            return Optional.empty();
        }
        if ((newCost > targetItemWeight / 2) || newCost > targetItemWeight - cost) {
            return Optional.empty();
        }
        batch.addNewQuest(this.batch.getUUID(), id);
        return Optional.empty();
    }

    private static int computeCosts(ServerLevel level, ResourceLocation qID, int stopAt) {
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
