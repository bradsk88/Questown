package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.AddRandomUpgradeQuest;
import ca.bradj.questown.town.rewards.ChangeJobReward;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import joptsimple.internal.Strings;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ca.bradj.questown.roomrecipes.Matches.getTopMatch;
import static ca.bradj.questown.roomrecipes.Matches.runForTopMatch;

public class TownQuests implements QuestBatch.ChangeListener<MCQuest>,
        ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>> {
    private @Nullable QuestBatchSeed pendingQuests = null;
    private final Stack<PendingReward> questRequests = new Stack<>();
    final MCQuestBatches questBatches = new MCQuestBatches(MCQuestBatch::new);
    private final UnsafeTown town = new UnsafeTown(getClass());

    TownQuests() {
        questBatches.addChangeListener(this);
    }

    public static void setUpQuestsForNewlyPlacedFlag(
            TownInterface town,
            TownQuests quests
    ) {
        MCRewardList reward = defaultQuestCompletionRewards(town);

        MCQuestBatch fireQuest = new MCQuestBatch(null, null, new MCDelayedReward(town, reward));
        fireQuest.addNewQuest(null, SpecialQuests.CAMPFIRE);

        quests.questBatches.add(fireQuest);
    }

    @NotNull
    private static MCRewardList defaultQuestCompletionRewards(TownInterface town) {
        // This is where a lot of the "progression" logic for Questown happens.
        // Changing this may significantly affect the feel of the game.
        UUID nextVisitorUUID = UUID.randomUUID();
        MCRewardList newVisitor = new MCRewardList(
                town,
                new SpawnVisitorReward(town, nextVisitorUUID),
                new AddBatchOfRandomQuestsForVisitorReward(town, nextVisitorUUID)
        );
        if (town.getQuestHandle().getVillagersWithQuests().isEmpty()) {
            // Spawn a villager with a set of quests
            return newVisitor;
        }

        UUID randomVillager = town.getRandomVillager();
        if (Compat.getRandomBool(town.getServerLevel()) && randomVillager != null) {
            // Add upgrades for an existing villager's quests
            return new MCRewardList(town, new AddRandomUpgradeQuest(town, randomVillager));
        }

        return newVisitor;
    }

    public static void addUpgradeQuest(
            TownInterface town,
            TownQuests quests,
            UUID visitorUUID
    ) {
        MCRewardList reward = defaultQuestCompletionRewards(town);

        Collection<MCQuest> completed = quests.getAllForVillager(visitorUUID).stream().filter(Quest::isComplete)
                                              .toList();
        Collection<MCQuest> villagerQuests = completed.stream()
                                                      // TODO: Filter out recipes that have already been slated for upgrade?
                                                      .filter(v -> v.fromRecipeID().isEmpty()).toList();

        // Prefer upgrading non-upgraded quests, but move up to the next tier if there are none left
        if (villagerQuests.isEmpty()) {
            villagerQuests = completed;
        }

        if (villagerQuests.isEmpty()) {
            // TODO: Add a failure path
            Questown.LOGGER.error("No upgrade paths could be determined. This is a bug and may cause softlock.");
            return;
        }

        ImmutableList<MCQuest> questsList = ImmutableList.copyOf(villagerQuests);

        int index = town.getServerLevel().getRandom().nextInt(questsList.size());
        MCQuest quest = questsList.get(index);

        ResourceLocation upgradeRecipe = getUpgradeRecipe(town.getServerLevel(), quest.getWantedId());
        if (upgradeRecipe == null) {
            // TODO: Add a failure path
            Questown.LOGGER.error("No upgrade paths could be determined. This is a bug and may cause softlock.");
            return;
        }

        MCQuestBatch upgradeQuest = new MCQuestBatch(UUID.randomUUID(), visitorUUID, new MCDelayedReward(town, reward));
        upgradeQuest.addNewUpgradeQuest(visitorUUID, quest.getWantedId(), upgradeRecipe);

        quests.questBatches.add(upgradeQuest);
    }

    public static void addJobQuest(
            TownFlagBlockEntity town,
            TownQuests quests,
            UUID visitorUUID
    ) {
        List<String> jobs = ImmutableList.copyOf(town.getAvailableRootJobs());
        int jobIdx = Compat.getRandomInt(town.getServerLevel(), jobs.size());
        String job = jobs.get(jobIdx);
        MCRewardList reward = new MCRewardList(
                town, new ChangeJobReward(town, visitorUUID, job),
                // TODO: Randomize? Maybe do EITHER new villager or more quests
                new AddBatchOfRandomQuestsForVisitorReward(town, town.getRandomVillager())
        );

        MCQuestBatch jobQuest = new MCQuestBatch(UUID.randomUUID(), visitorUUID, new MCInstantReward(town, reward));
        jobQuest.addNewQuest(visitorUUID, ServerJobsRegistry.getRoomForJobRootId(town.getServerLevel(), job));
        if (!town.getWorkHandle().hasAtLeastOneBoard()) {
            jobQuest.addNewQuest(visitorUUID, SpecialQuests.JOB_BOARD);
        }

        quests.questBatches.add(jobQuest);
    }

    private static @Nullable ResourceLocation getUpgradeRecipe(
            Level level,
            ResourceLocation fromRecipeId
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
        return ing.stream()
                  .map(v -> Arrays.stream(v.getItems()).map(ItemStack::getItem).map(ForgeRegistries.ITEMS::getKey)
                                  .filter(Objects::nonNull).map(ResourceLocation::toString).toList()).toList();
    }

    public static void addRandomBatchForVisitor(
            TownInterface town,
            TownQuests quests,
            @Nullable UUID visitorUUID
    ) {
        @NotNull MCRewardList reward = defaultQuestCompletionRewards(town);
        quests.questRequests.add(new PendingReward(visitorUUID, reward));
    }

    public static ImmutableSet<UUID> getVillagers(TownQuests quests) {
        return ImmutableSet.copyOf(quests.questBatches.getAllBatches().stream().map(MCQuestBatch::getOwner)
                                                      .filter(Objects::nonNull).collect(Collectors.toSet()));
    }

    public void tick(TownInterface town) {
        // TODO: Check if target weight (based on town size) has changed since last tick
        //  If it has, discard the pending quests and start over.
        // FIXME: When a player discards a quest batch, the new batch is generated
        //  with a bigger target size than it should have. Because there are now more
        //  villagers in town than there were when the original batch was generated.
        ServerLevel level = town.getServerLevel();
        int targetItemWeight = Config.MIN_WEIGHT_PER_QUEST_BATCH.get() + (Config.QUEST_BATCH_VILLAGER_BOOST_FACTOR.get() * (getVillagers(
                this).size() + 2)) / 2;
        if (pendingQuests == null) {
            QT.QUESTS_LOGGER.debug("Preparing quest batch with target weight: {}", targetItemWeight);
            pendingQuests = new QuestBatchSeed(level, UUID.randomUUID(), targetItemWeight);
        }

        QuestBatchSeed pop = pendingQuests;
        pendingQuests = null;

        boolean canGrowMore = pop.grow(
                town::hasEnoughBeds,
                () -> getNeededRooms(town.getEconomicsHandle()).stream().filter(v -> TownQuests.isNotSpecial(v.id()))
                                                               .toList(),
                () -> {
                    List<RoomRecipe> recipes = level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).stream()
                                                    .filter(v -> TownQuests.isNotSpecial(v.getId())).toList();
                    List<ResourceLocation> ids = recipes.stream().map(RoomRecipe::getId).toList();
                    return ids;
                }
        );

        if (canGrowMore) {
            QT.QUESTS_LOGGER.debug(
                    "Batch after growth is: ({}/{})[{}]",
                    pop.getCostSoFar(),
                    targetItemWeight,
                    Strings.join(
                            pop.get().getAll().stream().map(Quest::getWantedId).map(ResourceLocation::toString)
                               .toList(), ", "
                    )
            );
        }

        if (canGrowMore) {
            if (!questRequests.isEmpty()) {
                QT.QUESTS_LOGGER.warn("Quest batch was not ready when requested"); // TODO: Track how far behind we get
            }
            pendingQuests = pop;
            return;
        }

        if (!questRequests.isEmpty()) {
            PendingReward pr = questRequests.pop();
            MCDelayedReward rw = new MCDelayedReward(town, pr.reward());
            MCQuestBatch q = pop.get(rw, pr.owner());
            questBatches.add(q);
            QT.QUESTS_LOGGER.debug("Precompiled quest batch was given to {}: {}", pr.owner(), q.toNiceString());
            return;
        }

        pendingQuests = pop; // Can't grow more (at the moment) and not needed. Push back for next tick.
    }

    private static boolean isNotSpecial(ResourceLocation id) {
        if (SpecialQuests.FARM.equals(id)) {
            return true;
        }
        if (SpecialQuests.SPECIAL_QUESTS.containsKey(id)) {
            return false;
        }
        return true;
    }

    private ImmutableList<RoomNeed<ResourceLocation>> getNeededRooms(NoMCEconomics economicsHandle) {
        ImmutableList.Builder<RoomNeed<ResourceLocation>> b = ImmutableList.builder();
        for (RoomNeed<String> v : economicsHandle.getAggregatedRooms()) {
            b.add(new RoomNeed<>(new ResourceLocation(v.id()), v.timesNeeded(), v.timesNeeded()));
        }
        return b.build();
    }

    public void markQuestAsComplete(
            MCRoom room,
            ResourceLocation q
    ) {
        questBatches.markRecipeAsComplete(room, q);
    }

    public void markAsConverted(
            MCRoom room,
            ResourceLocation oldRecipeID,
            ResourceLocation newRecipeID
    ) {
        questBatches.markRecipeAsConverted(room, oldRecipeID, newRecipeID);
    }

    public void markQuestAsLost(
            MCRoom oldRoom,
            ResourceLocation recipeID
    ) {
        questBatches.markRecipeAsLost(oldRoom, recipeID);
    }


    @Override
    public void questCompleted(MCQuest quest) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        t.messages.questCompleted(quest);
        t.setChanged();
        FireworkRocketEntity firework = new FireworkRocketEntity(
                town.getServerLevelUnsafe(),
                t.getBlockPos().getX(),
                t.getBlockPos().getY() + 10,
                t.getBlockPos().getZ(),
                new ItemStack(Items.FIREWORK_ROCKET.getDefaultInstance().getItem(), 3)
        );
        town.getServerLevelUnsafe().addFreshEntity(firework);
    }

    @Override
    public void questLost(MCQuest quest) {
        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        t.messages.questLost(quest);
        t.setChanged();
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        // TODO: Handle this by informing the user, etc.
        town.getUnsafe().setChanged();
    }

    public ImmutableList<Quest<ResourceLocation, MCRoom>> getAll() {
        return ImmutableList.copyOf(questBatches.getAll().stream().map(v -> (Quest<ResourceLocation, MCRoom>) v)
                                                .toList());
    }

    public ImmutableMap<Quest<ResourceLocation, MCRoom>, MCReward> getAllWithRewards() {
        ImmutableMap.Builder<Quest<ResourceLocation, MCRoom>, MCReward> b = ImmutableMap.builder();
        questBatches.getAllWithRewards().forEach(b::put);
        return b.build();
    }

    public Collection<MCQuest> getAllForVillager(UUID uuid) {
        return this.questBatches.getAllBatches().stream().filter(b -> uuid.equals(b.getOwner()))
                                .flatMap(v -> v.getAll().stream()).toList();
    }

    public List<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllForVillagerWithRewards(UUID uuid) {
        return this.questBatches.getAllForVillagerWithRewards(uuid);
    }

    public void addBatch(MCQuestBatch batch) {
        questBatches.add(batch);
    }

    public Collection<MCQuestBatch> getBatches() {
        return this.questBatches.getAllBatches();
    }

    public boolean canBeUpgraded(
            ResourceLocation fromRecipeID,
            ResourceLocation toRecipeID
    ) {
        ImmutableList<Quest<ResourceLocation, MCRoom>> all = this.getAll();
        return all.stream().filter(Predicates.not(Quest::isComplete))
                  .anyMatch(matchesToUpgrade(fromRecipeID, toRecipeID));
    }

    @NotNull
    private static Predicate<Quest<ResourceLocation, MCRoom>> matchesToUpgrade(
            ResourceLocation from,
            ResourceLocation to
    ) {
        return v -> v.getWantedId().equals(to) && v.fromRecipeID().map(z -> z.equals(from)).orElse(false);
    }

    public void changeRoomOnly(
            MCRoom oldRoom,
            MCRoom newRoom
    ) {
        questBatches.changeRoomOnly(oldRoom, newRoom);
    }

    public boolean alreadyRequested(ResourceLocation resourceLocation) {
        // TODO: If new quest is an UPGRADE of an existing UNFINISHED quest, also make it more costly.
        return getAll().stream().map(Quest::getWantedId).anyMatch(v -> v.equals(resourceLocation));
    }

    @Override
    public void roomRecipeCreated(
            MCRoom room,
            RoomRecipeMatch<MCRoom> match
    ) {
        ServerLevel l = town.getServerLevelUnsafe();
        runForTopMatch(this::recipesFromLevel, match, r -> markQuestAsComplete(room, r));
    }

    private Map<ResourceLocation, RoomRecipe> recipesFromLevel() {
        return RoomRecipes.hydrate(town.getServerLevelUnsafe().getRecipeManager(), true);
    }

    @Override
    public void roomRecipeChanged(
            MCRoom oldRoom,
            RoomRecipeMatch<MCRoom> oldMatch,
            MCRoom newRoom,
            RoomRecipeMatch<MCRoom> newMatch
    ) {
        Optional<ResourceLocation> oldMatchID = getTopMatch(this::recipesFromLevel, oldMatch);
        Optional<ResourceLocation> newMatchID = getTopMatch(this::recipesFromLevel, newMatch);
        if (oldMatchID.isEmpty() && newMatchID.isPresent()) {
            markQuestAsComplete(newRoom, newMatchID.get());
            return;
        }
        if (oldMatchID.equals(newMatchID)) {
            if (!oldRoom.equals(newRoom)) {
                changeRoomOnly(oldRoom, newRoom);
            }
        }
        if (!oldMatchID.equals(newMatchID)) {
            if (oldMatchID.isPresent() && newMatchID.isPresent()) {
                if (canBeUpgraded(oldMatchID.get(), newMatchID.get())) {
                    markAsConverted(newRoom, oldMatchID.get(), newMatchID.get());
                } else {

                    markQuestAsLost(oldRoom, oldMatchID.get());
                    markQuestAsComplete(newRoom, newMatchID.get());
                }
            }
        }
    }

    @Override
    public void roomRecipeDestroyed(
            MCRoom room,
            RoomRecipeMatch<MCRoom> oldMatch
    ) {
        runForTopMatch(this::recipesFromLevel, oldMatch, rl -> markQuestAsLost(room, rl));
    }

    public void initialize(TownFlagBlockEntity t) {
        this.town.initialize(t);
    }
}
