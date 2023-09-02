package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.AddRandomUpgradeQuest;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TownQuests implements QuestBatch.ChangeListener<MCQuest> {
    private final Stack<PendingQuests> pendingQuests = new Stack<>();
    final MCQuestBatches questBatches = new MCQuestBatches();
    private QuestBatch.ChangeListener<MCQuest> changeListener;

    TownQuests() {
        questBatches.addChangeListener(this);
    }

    public static void setUpQuestsForNewlyPlacedFlag(TownInterface town, TownQuests quests) {
        MCRewardList reward = defaultQuestCompletionRewards(town);

        MCQuestBatch fireQuest = new MCQuestBatch(null, new MCDelayedReward(town, reward));
        fireQuest.addNewQuest(SpecialQuests.CAMPFIRE);

        quests.questBatches.add(fireQuest);
    }

    @NotNull
    private static MCRewardList defaultQuestCompletionRewards(TownInterface town) {
        // This is where a lot of the "progression" logic for Questown happens.
        // Changing this may significantly affect the feel of the game.
        Random rand = town.getServerLevel().getRandom();
        if (town.getVillagers().isEmpty() || rand.nextBoolean()) {
            // Spawn a villager with a set of quests
            UUID nextVisitorUUID = UUID.randomUUID();
            return new MCRewardList(
                    town,
                    new SpawnVisitorReward(town, nextVisitorUUID),
                    new AddBatchOfRandomQuestsForVisitorReward(town, nextVisitorUUID)
            );
        } else {
            // Add upgrades for an existing villager's quests
            return new MCRewardList(town, new AddRandomUpgradeQuest(town, town.getRandomVillager(rand)));
        }
    }

    public static void addUpgradeQuest(TownInterface town, TownQuests quests, UUID visitorUUID) {
        MCRewardList reward = defaultQuestCompletionRewards(town);

        Collection<MCQuest> completed = quests.getAllForVillager(visitorUUID).stream().filter(Quest::isComplete).toList();
        Collection<MCQuest> villagerQuests = completed.stream()
                // TODO: Filter out recipes that have already been slated for upgrade?
                .filter(v -> v.fromRecipeID().isEmpty()).toList();

        // Prefer upgrading non-upgraded quests, but move up to the next tier if there are none left
        if (villagerQuests.isEmpty()) {
            villagerQuests = completed;
        }

        if (villagerQuests.isEmpty()) {
            // FIXME: Add a failure path
            Questown.LOGGER.error("No upgrade paths could be determined. This is a bug and may cause softlock.");
            return;
        }

        ImmutableList<MCQuest> questsList = ImmutableList.copyOf(villagerQuests);

        int index = town.getServerLevel().getRandom().nextInt(questsList.size());
        MCQuest quest = questsList.get(index);

        ResourceLocation upgradeRecipe = getUpgradeRecipe(town.getServerLevel(), quest.getWantedId());
        if (upgradeRecipe == null) {
            // FIXME: Add a failure path
            Questown.LOGGER.error("No upgrade paths could be determined. This is a bug and may cause softlock.");
            return;
        }

        MCQuestBatch upgradeQuest = new MCQuestBatch(visitorUUID, new MCDelayedReward(town, reward));
        upgradeQuest.addNewUpgradeQuest(quest.getWantedId(), upgradeRecipe);

        quests.questBatches.add(upgradeQuest);
    }

    private static @Nullable ResourceLocation getUpgradeRecipe(
            Level level, ResourceLocation fromRecipeId
    ) {
        Optional<RoomRecipe> fromRecipe = RoomRecipes.getById(level, fromRecipeId);
        if (fromRecipe.isEmpty()) {
            return null;
        }
        NonNullList<Ingredient> fromIngredients = fromRecipe.get().getIngredients();


        List<RoomRecipe> all = RoomRecipes.getAllRecipes(level);

        ImmutableList.Builder<ResourceLocation> possibleUpgrades = ImmutableList.builder();

        for (RoomRecipe aRecipe : all) {
            Collection<List<String>> toIng = getItemKeyStrings(aRecipe.getIngredients());
            Collection<List<String>> fromIng = getItemKeyStrings(fromIngredients);
            if (toIng.equals(fromIng)) {
                continue; // Perfect overlap. So not an upgrade.
            }
            if (RoomRecipes.containsAllTags(fromIng, toIng)) {
                possibleUpgrades.add(aRecipe.getId());
            }
        }

        ImmutableList<ResourceLocation> upgrades = possibleUpgrades.build();
        if (upgrades.isEmpty()) {
            return null;
        }

        return upgrades.get(level.getRandom().nextInt(upgrades.size()));
    }

    @NotNull
    private static List<List<String>> getItemKeyStrings(NonNullList<Ingredient> ing) {
        return ing.stream().map(v -> Arrays.stream(v.getItems()).map(ItemStack::getItem).map(Item::getRegistryName).filter(Objects::nonNull).map(ResourceLocation::toString).toList()).toList();
    }

    public static void addRandomBatchForVisitor(TownInterface town, TownQuests quests, UUID visitorUUID) {
        @NotNull MCRewardList reward = defaultQuestCompletionRewards(town);
        int minItems = 100 + (100 * (getVillagers(quests).size() + 1)) / 2;
        PendingQuests theNewQuests = new PendingQuests(minItems, visitorUUID, new MCDelayedReward(town, reward));
        quests.pendingQuests.push(theNewQuests);
    }

    public static Set<UUID> getVillagers(TownQuests quests) {
        return quests.questBatches.getAllBatches().stream().map(MCQuestBatch::getOwner).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public void tick(ServerLevel sl) {


        if (!pendingQuests.isEmpty()) {
            PendingQuests pop = pendingQuests.pop();
            Optional<MCQuestBatch> o = pop.grow(sl);
            o.ifPresentOrElse(questBatches::add, () -> pendingQuests.push(pop));
        }

    }

    public void markQuestAsComplete(MCRoom room, ResourceLocation q) {
        questBatches.markRecipeAsComplete(room, q);
    }

    public void markAsConverted(MCRoom room, ResourceLocation oldRecipeID, ResourceLocation newRecipeID) {
        questBatches.markRecipeAsConverted(room, oldRecipeID, newRecipeID);
    }

    public void markQuestAsLost(MCRoom oldRoom, ResourceLocation recipeID) {
        questBatches.markRecipeAsLost(oldRoom, recipeID);
    }

    @Override
    public void questCompleted(MCQuest quest) {
        this.changeListener.questCompleted(quest);
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        this.changeListener.questBatchCompleted(quest);
    }

    @Override
    public void questLost(MCQuest quest) {
        this.changeListener.questLost(quest);
    }

    public void setChangeListener(TownFlagBlockEntity townFlagBlockEntity) {
        this.changeListener = townFlagBlockEntity;
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAll() {
        return ImmutableList.copyOf(questBatches.getAll().stream().map(v -> (Quest<ResourceLocation, MCRoom>) v).toList());
    }

    public Collection<MCQuest> getAllForVillager(UUID uuid) {
        return this.questBatches.getAllBatches().stream().filter(b -> uuid.equals(b.getOwner())).flatMap(v -> v.getAll().stream()).toList();
    }

    public void addBatch(MCQuestBatch batch) {
        questBatches.add(batch);
    }

    public Collection<MCQuestBatch> getBatches() {
        return this.questBatches.getAllBatches();
    }

    public boolean canBeUpgraded(ResourceLocation fromRecipeID, ResourceLocation toRecipeID) {
        ImmutableList<Quest<ResourceLocation, MCRoom>> all = this.getAll();
        return all.stream().filter(Predicates.not(Quest::isComplete)).anyMatch(matchesToUpgrade(fromRecipeID, toRecipeID));
    }

    @NotNull
    private static Predicate<Quest<ResourceLocation, MCRoom>> matchesToUpgrade(ResourceLocation from, ResourceLocation to) {
        return v -> v.getWantedId().equals(to) && v.fromRecipeID().map(z -> z.equals(from)).orElse(false);
    }

    public void changeRoomOnly(MCRoom oldRoom, MCRoom newRoom) {
        questBatches.changeRoomOnly(oldRoom, newRoom);
    }
}
