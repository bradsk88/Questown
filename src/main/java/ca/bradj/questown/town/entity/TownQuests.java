package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.town.quests.paths.QuestPath;
import ca.bradj.questown.town.quests.paths.QuestPathProgress;
import ca.bradj.questown.town.quests.paths.QuestPaths;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.RecipeRef;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.*;
import ca.bradj.questown.town.special.SpecialQuests;import ca.bradj.roomrecipes.adapter.Positions;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ca.bradj.questown.roomrecipes.Matches.getTopMatch;
import static ca.bradj.questown.roomrecipes.Matches.runForTopMatch;

public class TownQuests implements QuestBatch.ChangeListener<MCQuest>,
        ActiveRecipes.ChangeListener<MCRoom, RoomRecipeMatch<MCRoom>> {
    private static final QuestBatches.Tracker<ResourceLocation, MCTownItem, MCQuest> TRACKER = new QuestBatches.Tracker<ResourceLocation, MCTownItem, MCQuest>() {
        @Override
        public int addCount(
                Map<ResourceLocation, Integer> map,
                MCTownItem stack
        ) {
            return map.merge(
                    Compat.getItemId(stack.get()),
                    stack.toMCItemStack().getCount(),
                    Integer::sum
            );
        }

        @Override
        public void removeCount(
                Map<ResourceLocation, Integer> map,
                MCQuest mcQuest
        ) {
            map.merge(
                    mcQuest.getWantedId(),
                    -mcQuest.getCountNeeded(),
                    Integer::sum
            );
        }
    };
    final MCQuestBatches questBatches = new MCQuestBatches(MCQuestBatch::new);

    /** Whether any quest is still incomplete, for the "!" over the flag. */
    public boolean hasIncompleteQuests() {
        return questBatches.getAll().stream().anyMatch(q -> !q.isComplete());
    }

    /** The keys of the incomplete quests, for the quests tab's seen check. */
    public ImmutableSet<String> incompleteQuestKeys() {
        return questBatches.getAll().stream()
                .filter(q -> !q.isComplete())
                .map(q -> q.getBatchUUID() + "|" + q.getWantedId())
                .collect(ImmutableSet.toImmutableSet());
    }

    private final Stack<PendingReward> questRequests = new Stack<>();
    private final UnsafeTown town = new UnsafeTown(getClass());
    boolean playerDiscardedLastBatch;
    private @Nullable QuestBatchSeed pendingQuests = null;

    TownQuests() {
        questBatches.addChangeListener(this);
    }

    /** The first day's quest batches: the campfire, then the town gate (a welcome mat). The gate brings the first villager and the empty-room batch, which leads to the basics. */
    public static void setUpQuestsForNewlyPlacedFlag(
            TownInterface town,
            TownQuests quests
    ) {
        MCQuestBatch fireQuest = new MCQuestBatch(null, null, new MCInstantReward(town, new CampfireReadyReward(town)));
        fireQuest.addNewQuest(null, SpecialQuests.CAMPFIRE);
        quests.questBatches.add(fireQuest);

        UUID gateUUID = UUID.randomUUID();
        MCQuestBatch.Inputs gate = new MCQuestBatch.Inputs(gateUUID, null);
        MCQuest gateQuest = MCQuest.standalone(gateUUID, null, SpecialQuests.TOWN_GATE);
        gateQuest.setFlavorText("Craft a welcome mat at the town flag (flag > crafting tab) and place it at your town's gate - someone will arrive here in the morning.");
        gate.addNewQuest(gateQuest);
        MCRewardList gateReward = new MCRewardList(
                town,
                new MCDelayedReward(town, new SpawnVisitorReward(town, VillagerUUID.random())),
                new MCInstantReward(town, new AddEmptyRoomBatchReward(town))
        );
        MCQuestBatch gateBatch = gate.withRewardUponCompletion(gateReward);
        markOnboarding(gateBatch);
        quests.questBatches.add(gateBatch);
    }

    /** The empty-room batch: one room quest any room with a registered door completes. Its reward is the basics batch. */
    void addEmptyRoomBatch() {
        TownFlagBlockEntity t = this.town.getUnsafe();
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        MCQuest emptyRoomQuest = roomQuest(batchUUID, SpecialQuests.EMPTY_ROOM);
        emptyRoomQuest.setFlavorText(
                "Build a room: four walls and a door. Use your town wand to register the door.");
        q.addNewQuest(emptyRoomQuest);
        MCQuestBatch qb = q.withRewardUponCompletion(
                new MCInstantReward(t, new AddBasicsBatchReward(t)));
        markOnboarding(qb);
        questBatches.add(qb);
        t.messages.broadcastMessage("New quest is available: build a small room and register its door.");
        QT.QUESTS_LOGGER.info("Posted the empty-room quest batch");
    }

    /** The basics batch (job board and storeroom). Its reward brings the second villager and the next batch. */
    void addBasicsBatch() {
        TownFlagBlockEntity t = this.town.getUnsafe();
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest jobBoardQuest = roomQuest(batchUUID, SpecialQuests.JOB_BOARD);
        q.addNewQuest(jobBoardQuest);

        MCQuest storeRoomQuest = roomQuest(batchUUID, SpecialQuests.STORE_ROOM_SMALL);
        storeRoomQuest.setFlavorText("Place a chest in a room with a wand-registered door.");
        q.addNewQuest(storeRoomQuest);

        MCQuestBatch qb = q.withRewardUponCompletion(
                new MCRewardList(
                        t,
                        TownFlagTutorialAdapter.from(t, questBatches).makeReward(TutorialTownView.PHASE_KICKOFF),
                        // Day 2: once the basics are done, the job board gets its first-request quest.
                        new MCInstantReward(t, new PostFirstWorkRequestQuestReward(t))
                ));
        markOnboarding(qb);
        questBatches.add(qb);
        t.messages.broadcastMessage("New quests are available! Right-click the town flag to see them.");
        QT.QUESTS_LOGGER.info("Basics batch posted (job board + store room) on welcome-mat completion");
    }

    /** Day 2's "post your first work request" quest. It completes when the player posts a request; automatic requests don't count. Its reward is a Block of Progress. */
    void addFirstWorkRequestBatch() {
        TownFlagBlockEntity t = this.town.getUnsafe();
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        q.addNewQuest(MCQuest.firstWorkRequest(batchUUID, null));
        MCQuestBatch qb = q.withRewardUponCompletion(
                new MCInstantReward(t, new GrantBlockOfProgressReward(t, 1)));
        questBatches.add(qb);
        QT.QUESTS_LOGGER.info("First-work-request batch posted (day-2 board introduction)");
    }

    /** Completes the first-work-request quest when the player posts a request. */
    public void markFirstWorkRequestCompleteIfActive() {
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.FIRST_WORK_REQUEST || q.isComplete()) {
                    continue;
                }
                batch.markRecipeAsComplete(null, q.getWantedId());
                QT.QUESTS_LOGGER.info("First-work-request quest completed on first posted request");
                return;
            }
        }
    }

    /** Completes the open skill-unlock quests on this skill's tree; one that names a skill completes only for that skill. */
    public void markSkillUnlocked(JobID unlocked) {
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.SKILL_UNLOCK || q.isComplete()) {
                    continue;
                }
                if (!MCQuest.skillTreeRoot(q.getWantedId()).equals(unlocked.rootId())) {
                    continue;
                }
                if (MCQuest.namedSkill(q.getWantedId()).map(s -> !s.equals(unlocked)).orElse(false)) {
                    continue;
                }
                batch.markRecipeAsComplete(null, q.getWantedId());
                QT.QUESTS_LOGGER.info("Skill-unlock quest completed: {} unlocked", unlocked.toNiceString());
            }
        }
    }

    /** Completes every open villager-level quest whose job now has a villager at that level. */
    void markVillagerLevelsReached(Map<String, Integer> highestLevelByRoot) {
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.VILLAGER_LEVEL || q.isComplete()) {
                    continue;
                }
                Optional<VillagerLevelQuests.Target> t = VillagerLevelQuests.target(q.getWantedId());
                if (t.isEmpty() || !VillagerLevelQuests.met(t.get(), highestLevelByRoot)) {
                    continue;
                }
                batch.markRecipeAsComplete(null, q.getWantedId());
                QT.QUESTS_LOGGER.info("Villager-level quest completed: a {} reached level {}", t.get().root(), t.get().level());
            }
        }
    }

    @NotNull
    private static final ImmutableSet<ResourceLocation> STORAGE_ROOMS = ImmutableSet.of(
            SpecialQuests.STORE_ROOM_SMALL,
            new ResourceLocation("questown", "store_room_medium"),
            new ResourceLocation("questown", "store_room_big"),
            new ResourceLocation("questown", "store_room_huge"),
            new ResourceLocation("questown", "storeage_facility")
    );

    /** When {@code reward} brings a villager, adds the bedroom quests its bed needs and a storage room quest. */
    static void provisionArrival(
            TownInterface town,
            MCReward reward,
            Collection<ResourceLocation> wanted,
            Consumer<ResourceLocation> addRoomQuest
    ) {
        ServerLevel level = town.getServerLevel();
        ArrivalProvisions plan = ArrivalProvisions.plan(
                reward.contains(RewardsInit.VISITOR.get()),
                town.missingBedsWithArrivals(1),
                (int) wanted.stream().filter(id -> QuestBatchSeed.isBedRoom(level, id)).count(),
                wanted.stream().anyMatch(STORAGE_ROOMS::contains)
        );
        for (int i = 0; i < plan.bedQuests(); i++) {
            addRoomQuest.accept(SpecialQuests.BEDROOM);
        }
        if (plan.storageQuest()) {
            addRoomQuest.accept(SpecialQuests.STORE_ROOM_SMALL);
        }
        if (!ArrivalProvisions.NONE.equals(plan)) {
            QT.QUESTS_LOGGER.info("Batch brings a villager: added {} bedroom quest(s), storage quest: {}",
                    plan.bedQuests(), plan.storageQuest());
        }
    }

    private static void provisionArrival(TutorialTownView view, MCQuestBatch batch) {
        view.provisionArrival(
                batch.getReward(),
                batch.getAll().stream().map(Quest::getWantedId).toList(),
                id -> batch.addNewQuest(null, id)
        );
    }

    public static MCRewardList defaultQuestCompletionRewards(TownInterface town) {
        // This is where a lot of the "progression" logic for Questown happens.
        // Changing this may significantly affect the feel of the game.

        VillagerUUID nextVisitorUUID = VillagerUUID.random();

        if (town.getVillagerHandle().size() == 1) {
            return new MCRewardList(
                    town,
                    new SpawnVisitorReward(town, nextVisitorUUID),
                    new AddItemQuestReward(town, Compat.getItemId(Items.APPLE), 10)
            );
        }

        MCRewardList newVisitor = new MCRewardList(
                town,
                new SpawnVisitorReward(town, nextVisitorUUID),
                new AddBatchOfQuestsForVisitorReward(town, VillagerUUID.get(nextVisitorUUID))
        );
        if (town.getQuestHandle().getVillagersWithQuests().isEmpty()) {
            // Spawn a villager with a set of quests
            return newVisitor;
        }

        VillagerUUID randomVillager = VillagerUUID.from(town.getRandomVillager());
        if (Compat.getRandomBool(town.getServerLevel()) && randomVillager != null) {
            // Add upgrades for an existing villager's quests
            return new MCRewardList(town, new AddRandomUpgradeQuest(town, randomVillager));
        }

        return newVisitor;
    }

    public static void addUpgradeQuest(
            TownInterface town,
            TownQuests quests,
            VillagerUUID visitorUUID
    ) {
        addUpgradeQuest(town, quests, visitorUUID, Set.of());
    }

    /** @param declinedFrom rooms whose upgrade the player just declined, so it isn't posted again */
    public static void addUpgradeQuest(
            TownInterface town,
            TownQuests quests,
            VillagerUUID visitorUUID,
            Set<ResourceLocation> declinedFrom
    ) {
        MCRewardList reward = defaultQuestCompletionRewards(town);

        Collection<MCQuest> villagerQuests = upgradeSources(quests.getAllForVillager(visitorUUID), declinedFrom);

        if (villagerQuests.isEmpty()) {
            QT.QUESTS_LOGGER.error(
                    "No upgrade paths could be determined because no quests have been completed yet for {}",
                    visitorUUID
            );
            QT.QUESTS_LOGGER.info("Skipping generation. The flag entity will generate a random batch instead.");
            addBatchForVisitor(town, quests, visitorUUID);
            return;
        }

        ImmutableList<MCQuest> upgradableQuests = Compat.shuffle(villagerQuests.iterator(), town.getServerLevel());

        for (MCQuest upgradable : upgradableQuests) {
            ResourceLocation upgradeFrom = upgradable.getWantedId(); // This is the room acquired by the (completed) quest
            ResourceLocation upgradeRecipe = getUpgradeRecipe(town.getServerLevel(), upgradeFrom);
            if (upgradeRecipe == null) {
                QT.QUESTS_LOGGER.debug("No upgrade recipe found for {}. Skipping.", upgradeFrom);
                continue;
            }

            MCQuestBatch upgradeQuest = new MCQuestBatch(
                    UUID.randomUUID(),
                    visitorUUID,
                    new MCDelayedReward(town, reward)
            );
            upgradeQuest.addNewUpgradeQuest(visitorUUID, upgradeFrom, upgradeRecipe);
            quests.questBatches.add(upgradeQuest);
            return;
        }

        QT.QUESTS_LOGGER.info("No upgrade paths could be determined.");
        QT.QUESTS_LOGGER.info("Skipping generation. The flag entity will generate a random batch instead.");
        addBatchForVisitor(town, quests, visitorUUID);
    }

    public static void addJobQuest(
            TownFlagBlockEntity town,
            TownQuests quests,
            VillagerUUID visitorUUID
    ) {
        List<String> jobs = ImmutableList.copyOf(town.getAvailableRootJobs());
        int jobIdx = Compat.getRandomInt(town.getServerLevel(), jobs.size());
        String job = jobs.get(jobIdx);
        MCRewardList reward = new MCRewardList(
                town, new ChangeJobReward(town, VillagerUUID.get(visitorUUID), job),
                // TODO: Randomize? Maybe do EITHER new villager or more quests
                new AddBatchOfQuestsForVisitorReward(town, town.getRandomVillager())
        );

        MCQuestBatch jobQuest = new MCQuestBatch(UUID.randomUUID(), visitorUUID, new MCInstantReward(town, reward));
        jobQuest.addNewQuest(visitorUUID, ServerJobsRegistry.getRoomForJobRootId(town.getServerLevel(), job));
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

        Map<ResourceLocation, Collection<List<String>>> all = new HashMap<>();
        for (RecipeRef.RecipePair<RoomRecipe> p : RecipeRef.pairsOf(level.getRecipeManager(), RecipesInit.ROOM)) {
            all.put(p.id(), getItemKeyStrings(p.value().getIngredients()));
        }

        List<ResourceLocation> upgrades = RoomRecipes.upgradeCandidates(
                getItemKeyStrings(fromIngredients), all);
        if (upgrades.isEmpty()) {
            return null;
        }

        return upgrades.get(level.getRandom().nextInt(upgrades.size()));
    }

    @NotNull
    private static List<List<String>> getItemKeyStrings(NonNullList<Ingredient> ing) {
        return ing.stream()
                  .map(v -> Arrays.stream(v.getItems()).map(ItemStack::getItem).map(Loader::itemIdOf)
                                  .filter(Objects::nonNull).map(ResourceLocation::toString).toList()).toList();
    }

    /** Batch requests waiting to become procedural batches. */
    int pendingBatchRequests() {
        return questRequests.size();
    }

    /** The completed room quests an upgrade can build on: first-tier rooms if there are any, else upgraded ones, minus the declined. */
    static List<MCQuest> upgradeSources(
            Collection<MCQuest> villagerQuests,
            Set<ResourceLocation> declinedFrom
    ) {
        List<MCQuest> done = villagerQuests.stream()
                .filter(Quest::isComplete)
                .filter(q -> !declinedFrom.contains(q.getWantedId()))
                .toList();
        // TODO: Filter out recipes that have already been slated for upgrade?
        List<MCQuest> firstTier = done.stream().filter(q -> q.fromRecipeID().isEmpty()).toList();
        if (firstTier.isEmpty()) {
            return done;
        }
        return firstTier;
    }

    public static void addBatchForVisitor(
            TownInterface town,
            TownQuests quests,
            @Nullable VillagerUUID visitorUUID
    ) {
        // This function only defines the rewards and "requests" a new batch
        // See "TownQuests.tick(TownInterface town)" to understand the batch generation
        @NotNull MCRewardList reward = defaultQuestCompletionRewards(town);
        quests.questRequests.add(new PendingReward(visitorUUID, reward));
    }

    public static void addItemQuest(
            TownFlagBlockEntity t,
            TownQuests quests,
            ResourceLocation itemId,
            int count
    ) {
        @NotNull MCRewardList reward = defaultQuestCompletionRewards(t);
        MCQuestBatch batch = new MCQuestBatch(null, null, new MCDelayedReward(t, reward));
        batch.addItemQuest(null, itemId, count);
        quests.addBatch(batch);
    }

    public static void addLootQuest(
            TownFlagBlockEntity t,
            TownQuests quests,
            ResourceLocation itemId,
            int count
    ) {
        @NotNull MCRewardList reward = defaultQuestCompletionRewards(t);
        MCQuestBatch batch = new MCQuestBatch(null, null, new MCDelayedReward(t, reward));
        batch.addLootQuest(null, itemId, count);
        quests.addBatch(batch);
    }

    public static ImmutableSet<VillagerUUID> getVillagers(TownQuests quests) {
        return ImmutableSet.copyOf(quests.questBatches.getAllBatches().stream().map(MCQuestBatch::getOwner)
                                                      .filter(Objects::nonNull).collect(Collectors.toSet()));
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

    @NotNull
    private static Predicate<Quest<ResourceLocation, MCRoom>> matchesToUpgrade(
            ResourceLocation from,
            ResourceLocation to
    ) {
        return v -> v.getWantedId().equals(to) && v.fromRecipeID().map(z -> z.equals(from)).orElse(false);
    }

    public void tick(TownInterface town) {
        retireJobUnlockQuests();
        backfillSatisfiedQuests(town);
        postCookFollowUpIfDue(TownFlagTutorialAdapter.from(this.town.getUnsafe(), questBatches));
        postPathBatchesIfDue(TownFlagTutorialAdapter.from(this.town.getUnsafe(), questBatches));

        ServerLevel level = town.getServerLevel();
        int size = getVillagers(this).size();

        if (!questRequests.isEmpty()) {
            Tutorial r = addTutorialBatches(TownFlagTutorialAdapter.from(this.town.getUnsafe(), questBatches));
            switch (r) {
                case APPLIED -> {
                    this.questRequests.clear();
                    this.playerDiscardedLastBatch = false;
                    return;
                }
                case SKIPPED -> {
                    return;
                }
                case DONE -> {
                    // Move on to regular generation
                }
            }
        }

        if (town.getVillagerHandle().entities().isEmpty()) {
            return;
        }

        if (this.playerDiscardedLastBatch) {
            size = size - 1;
        }

        int targetItemWeight = Config.MIN_WEIGHT_PER_QUEST_BATCH.get() + (Config.QUEST_BATCH_VILLAGER_BOOST_FACTOR.get() * (size + 2)) / 2;
        // TODO: Check if target weight (based on town size) has changed since last tick
        //  If it has, discard the pending quests and start over.
        if (pendingQuests == null) {
            QT.QUESTS_LOGGER.debug("Preparing quest batch with target weight: {}", targetItemWeight);
            pendingQuests = new QuestBatchSeed(level, UUID.randomUUID(), targetItemWeight, this.town.getUnsafe().completedProceduralBatches);
        }

        QuestBatchSeed pop = pendingQuests;
        pendingQuests = null;

        boolean canGrowMore = pop.grow(
                town::missingBeds,
                () -> getNeededRooms(town.getEconomicsHandle()).stream().filter(v -> TownQuests.isNotSpecial(v.id()))
                                                               .toList(),
                () -> {
                    List<ResourceLocation> ids = new ArrayList<>(
                            RecipeRef.idsOf(level.getRecipeManager(), RecipesInit.ROOM).stream()
                                    .filter(TownQuests::isNotSpecial)
                                    .toList()
                    );
                    for (Supplier<RoomBlock> roomBlockSupplier : BlockAsRoomEntity.ALL) {
                        ids.add(roomBlockSupplier.get().asRecipe().getId());
                    }
                    return ids;
                }
        );

        if (canGrowMore) {
            town.getDebugLogger(QT.QUESTS_LOGGER, DebugLogArgument.QUEST_BATCH_COMPUTE_NEXT).log(
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
            provisionArrival(town, rw, q.getAll().stream().map(Quest::getWantedId).toList(),
                    id -> q.addNewQuest(null, id));
            q.getAll().forEach(quest -> quest.setDiscardable(true));
            questBatches.add(q);
            QT.QUESTS_LOGGER.debug("Precompiled quest batch was given to {}: {}", pr.owner(), q.toNiceString());
            playerDiscardedLastBatch = false;
            return;
        }

        pendingQuests = pop; // Can't grow more (at the moment) and not needed. Push back for next tick.
    }

    /** Withdraws the open JOB_UNLOCK quests left in older saves. A top-need batch keeps its room quest. */
    void retireJobUnlockQuests() {
        retireJobUnlockQuests(questBatches.getAllBatches(), questBatches::decline);
    }

    static void retireJobUnlockQuests(
            Collection<MCQuestBatch> batches,
            Consumer<MCQuestBatch> decline
    ) {
        for (MCQuestBatch batch : ImmutableList.copyOf(batches)) {
            for (MCQuest q : ImmutableList.copyOf(batch.getAll())) {
                if (q.getType() != Quest.QuestType.JOB_UNLOCK || q.isComplete()) {
                    continue;
                }
                String producer = JobID.fromRL(q.getWantedId()).rootId();
                if (q.unlockStuckRoot().isEmpty()) {
                    tagTopNeedRoomQuests(batch, producer);
                }
                QT.QUESTS_LOGGER.info("Withdrawing retired JOB_UNLOCK quest for root {}", producer);
                if (batch.withdrawQuest(q) && batch.getAll().isEmpty()) {
                    decline.accept(batch);
                }
            }
        }
    }

    private static void tagTopNeedRoomQuests(
            MCQuestBatch batch,
            String producer
    ) {
        batch.getAll().stream()
             .filter(q -> q.getType() == Quest.QuestType.ROOM)
             .forEach(q -> q.setTopNeedProducer(producer));
    }

    static boolean topNeedBeatOpen(Collection<? extends Quest<?, ?>> quests) {
        return quests.stream().anyMatch(q -> !q.isComplete() && q.topNeedProducer().isPresent());
    }

    /** Producers whose top-need room the town already built, so it isn't asked for again. */
    static Set<String> builtTopNeedProducers(Collection<? extends Quest<?, ?>> quests) {
        Set<String> built = new HashSet<>();
        quests.stream()
              .filter(Quest::isComplete)
              .forEach(q -> q.topNeedProducer().ifPresent(built::add));
        return built;
    }

    /** The first producer root that isn't the stuck root and isn't unlocked by any villager. @param stuckRoot the root to skip, or null */
    @Nullable
    static String chooseProducerRoot(
            Set<String> producerRoots,
            @Nullable String stuckRoot,
            Set<String> unlockedRoots
    ) {
        return producerRoots.stream()
                            .filter(r -> stuckRoot == null || !r.equals(stuckRoot))
                            .filter(r -> !unlockedRoots.contains(r))
                            .sorted()
                            .findFirst()
                            .orElse(null);
    }

    /** The first need with a producer the town could unlock, and that producer; a null need means none. */
    record TopNeedChoice(@Nullable ItemEconomicsData need, @Nullable String producer) {
    }

    /** The first most-needed item a job not yet unlocked could make. Needs with no producer, or only unlocked ones, are skipped. */
    static TopNeedChoice chooseTopNeed(
            List<ItemEconomicsData> needs,
            Function<String, Set<String>> producerRootsFor,
            Set<String> unlockedRoots
    ) {
        for (ItemEconomicsData need : needs) {
            String key = need.ingredientKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            Set<String> roots = producerRootsFor.apply(key);
            if (roots == null || roots.isEmpty()) {
                continue;
            }
            String producer = chooseProducerRoot(roots, null, unlockedRoots);
            if (producer != null) {
                return new TopNeedChoice(need, producer);
            }
        }
        return new TopNeedChoice(null, null);
    }

    private static Set<String> unlockedRoots(TownInterface town) {
        Set<String> roots = new HashSet<>();
        for (ImmutableSet<JobID> jobs : town.getVillagerHandle().getUnlockedJobs().values()) {
            jobs.forEach(j -> roots.add(j.rootId()));
        }
        return roots;
    }

    public boolean isWaitingForHunterGathererTutorial() {
        // Past the first four batches, the overnight hunter override stops.
        if (questBatches.getAllBatches().size() > 4) {
            return false;
        }
        return questBatches.getAll().stream()
                           .filter(v -> Quest.QuestType.JOB_CHANGE.equals(v.getType()))
                           .anyMatch(v -> v.getWantedId().equals(JobID.toRL(new JobID("hunter", "sword"))));
    }

    enum Tutorial {
        SKIPPED,
        APPLIED,
        DONE
    }

    Tutorial addTutorialBatches(TutorialTownView view) {
        if (questBatches.isEmpty()) {
            return Tutorial.SKIPPED;
        }

        Tutorial r;
        r = tryPhase2_hunterGatherer(view);           if (r != null) return r;
        r = tryPhase2_4_emptyRooms(view);             if (r != null) return r;
        r = tryPhase2_5_jobChangeAndFood(view);       if (r != null) return r;
        r = tryPhase2_7_miner(view);                  if (r != null) return r;
        if (!curatedPaths.get().isEmpty()) {
            // After the miner lesson, the curated paths take over.
            r = tryPhase3_newJobRoom(view);           if (r != null) return r;
            r = tryCuratedPaths(view);                if (r != null) return r;
            return Tutorial.DONE;
        }
        r = tryPhase2_75_topNeedUnlock(view);         if (r != null) return r;
        r = tryPhase3_newJobRoom(view);               if (r != null) return r;
        r = tryPhase4_crafter(view);                  if (r != null) return r;
        r = tryPhase5_supplyChain(view);              if (r != null) return r;
        if (!supplyChainsTriggerFired) {
            view.fireTutorialTrigger(TutorialTrigger.Triggers.SecondJobType);
            supplyChainsTriggerFired = true;
        }
        return Tutorial.DONE;
    }

    private @Nullable Tutorial tryPhase2_hunterGatherer(TutorialTownView view) {
        // One villager: the second arrives only once this batch's raw-food quest is done.
        if (view.villagerCount() < 1) {
            return Tutorial.SKIPPED;
        }
        if (questBatches.includes(q -> q.getType() == Quest.QuestType.JOB_CHANGE)) {
            return null;
        }
        grantTutorialBop(view);
        addHunterGatherer(view);
        notifyNewQuests(view);
        return Tutorial.APPLIED;
    }

    /** A bedroom quest between the hunter-gatherer and cook batches, while villager 2 is on the way. */
    private @Nullable Tutorial tryPhase2_4_emptyRooms(TutorialTownView view) {
        if (cookBatchPosted()) {
            return null;
        }
        if (!hunterGathererBatchComplete()) {
            return Tutorial.SKIPPED;
        }
        if (!emptyRoomsBatchPosted()) {
            addNewcomerBedroomBatch(view);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (!emptyRoomsBatchComplete()) {
            return Tutorial.SKIPPED;
        }
        return null;
    }

    private boolean cookBatchPosted() {
        return questBatches.getAll().stream().filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE).count() >= 2;
    }

    private boolean hunterGathererBatchComplete() {
        ResourceLocation rawFood = TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location();
        return questBatches.getAllBatches().stream()
                .filter(b -> b.getAll().stream().anyMatch(q -> q.getType() == Quest.QuestType.LOOT && rawFood.equals(q.getWantedId())))
                .anyMatch(b -> b.getAll().stream().allMatch(Quest::isComplete));
    }

    /** The bedroom batch posted after the hunter-gatherer batch (an empty-room batch in older worlds). */
    private @Nullable MCQuestBatch emptyRoomsBatch() {
        ResourceLocation rawFood = TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location();
        boolean afterHunterGatherer = false;
        for (MCQuestBatch b : questBatches.getAllBatches()) {
            if (b.getAll().stream().anyMatch(q -> q.getType() == Quest.QuestType.LOOT && rawFood.equals(q.getWantedId()))) {
                afterHunterGatherer = true;
                continue;
            }
            if (afterHunterGatherer && !b.getAll().isEmpty()
                    && b.getAll().stream().allMatch(q -> POST_HUNTER_ROOMS.contains(q.getWantedId()))) {
                return b;
            }
        }
        return null;
    }

    private boolean emptyRoomsBatchPosted() {
        return emptyRoomsBatch() != null;
    }

    private boolean emptyRoomsBatchComplete() {
        MCQuestBatch b = emptyRoomsBatch();
        return b != null && b.getAll().stream().allMatch(Quest::isComplete);
    }

    private static final Set<ResourceLocation> POST_HUNTER_ROOMS = Set.of(SpecialQuests.BEDROOM, SpecialQuests.EMPTY_ROOM);

    void addNewcomerBedroomBatch(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        // A new bedroom: a room only completes one quest per recipe.
        q.addNewQuest(roomQuest(batchUUID, SpecialQuests.BEDROOM));
        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_EMPTY_ROOMS));
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.info("Tutorial bedroom batch added (a new bedroom before the cook batch)");
    }

    private @Nullable Tutorial tryPhase2_5_jobChangeAndFood(TutorialTownView view) {
        // Two villagers: the hunter, and the one who becomes the cook.
        if (view.villagerCount() < 2) {
            return Tutorial.SKIPPED;
        }
        if (questBatches.getAll().stream().filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE).count() < 2) {
            if (!hunterRawFoodLootComplete()) {
                return Tutorial.SKIPPED;
            }
            addQuestsForJobChangeAndFood(view);
            // This batch has the first room upgrade, so open the upgrades lesson.
            view.fireTutorialTrigger(TutorialTrigger.Triggers.FirstRoomUpgrade);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (questBatches.getAll().stream().filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE && q.isComplete()).count() < 2) {
            return Tutorial.SKIPPED;
        }
        return null;
    }

    /** The miner lesson after the cook: the mineshaft, pickaxes, some coal, and a job change to miner. */
    private @Nullable Tutorial tryPhase2_7_miner(TutorialTownView view) {
        if (jobChangesComplete() < 2) {
            return Tutorial.SKIPPED;
        }
        if (view.villagerCount() < 3) {
            return Tutorial.SKIPPED;
        }
        if (minerBeatPosted()) {
            // Posted once.
            return null;
        }
        if (questBatches.includes(q -> !q.isComplete() && q.getType() == Quest.QuestType.JOB_CHANGE)) {
            // Wait for the open job change to land first.
            return null;
        }
        addMiner(view);
        notifyNewQuests(view);
        return Tutorial.APPLIED;
    }

    Supplier<List<QuestPath>> curatedPaths = QuestPaths::all;
    // Paths whose finishing advancement fired this session.
    private final Set<String> announcedPaths = new HashSet<>();

    /** True once the onboarding miner batch is done; the paths start here. */
    boolean atTheFork() {
        ResourceLocation minerJob = JobID.toRL(ONBOARDING_MINER_JOB);
        return questBatches.getAllBatches().stream()
                .filter(b -> b.getAll().stream().anyMatch(q -> q.isOnboarding()
                        && q.getType() == Quest.QuestType.JOB_CHANGE
                        && minerJob.equals(q.getWantedId())))
                .anyMatch(b -> b.getAll().stream().allMatch(Quest::isComplete));
    }

    /** Posts every path batch that is due: starters at the fork, then each batch whose prerequisites are done. Runs every tick. */
    void postPathBatchesIfDue(TutorialTownView view) {
        if (!atTheFork()) {
            return;
        }
        boolean postedAny = false;
        for (QuestPath path : curatedPaths.get()) {
            Set<String> posted = pathBatchIds(path, false);
            Set<String> done = pathBatchIds(path, true);
            for (QuestPath.Batch b : QuestPathProgress.toPost(path, posted, done)) {
                if (b.isStarter()) {
                    view.fireTutorialTrigger(TutorialTrigger.Triggers.TutorialComplete);
                }
                addPathBatch(view, path, b);
                postedAny = true;
            }
            announceIfFinished(view, path, posted, done);
        }
        if (postedAny) {
            notifyNewQuests(view);
        }
    }

    /** Fires a finished path's advancement, once per session. */
    private void announceIfFinished(TutorialTownView view, QuestPath path, Set<String> posted, Set<String> done) {
        if (announcedPaths.contains(path.id()) || !QuestPathProgress.taken(path, done)) {
            return;
        }
        if (!QuestPathProgress.finished(path, posted, done)) {
            return;
        }
        announcedPaths.add(path.id());
        TutorialTrigger.Triggers.forPath(path.id()).ifPresent(view::fireTutorialTrigger);
        QT.QUESTS_LOGGER.info("Quest path finished: {}", path.id());
    }

    /** While a path has batches left, the procedural quests wait. */
    @Nullable Tutorial tryCuratedPaths(TutorialTownView view) {
        if (!atTheFork()) {
            return Tutorial.SKIPPED;
        }
        postPathBatchesIfDue(view);
        boolean anyTaken = false;
        for (QuestPath path : curatedPaths.get()) {
            Set<String> done = pathBatchIds(path, true);
            if (!QuestPathProgress.taken(path, done)) {
                continue;
            }
            anyTaken = true;
            if (!QuestPathProgress.finished(path, pathBatchIds(path, false), done)) {
                return Tutorial.SKIPPED;
            }
        }
        return anyTaken ? null : Tutorial.SKIPPED;
    }

    /** The ids of {@code path}'s batches this town has posted, or only those completed. */
    Set<String> pathBatchIds(QuestPath path, boolean completeOnly) {
        String prefix = path.id() + "/";
        Map<String, Boolean> allComplete = new HashMap<>();
        for (MCQuest q : questBatches.getAll()) {
            q.pathBatch()
             .filter(t -> t.startsWith(prefix))
             .ifPresent(t -> allComplete.merge(t.substring(prefix.length()), q.isComplete(), Boolean::logicalAnd));
        }
        if (!completeOnly) {
            return allComplete.keySet();
        }
        return allComplete.entrySet().stream()
                          .filter(Map.Entry::getValue)
                          .map(Map.Entry::getKey)
                          .collect(Collectors.toSet());
    }

    private void addPathBatch(TutorialTownView view, QuestPath path, QuestPath.Batch batch) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        for (QuestPath.Step s : batch.quests()) {
            q.addNewQuest(pathQuest(batchUUID, s));
            if (s.kind() == QuestPath.Kind.SKILL) {
                view.revealSkill(pathJob(s.id()));
            }
        }
        String phase = batch.arrival() ? TutorialTownView.PHASE_PATH_ARRIVAL : TutorialTownView.PHASE_PATH;
        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(phase));
        if (batch.arrival()) {
            provisionArrival(view, qq);
        }
        // Tag after adding the arrival's quests, so they count toward the batch.
        qq.getAll().forEach(quest -> quest.setPathBatch(path.batchTag(batch)));
        // A path's job change gets the promised job and applies at the spend.
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.info("Quest path batch posted: {}", path.batchTag(batch));
    }

    private static MCQuest pathQuest(UUID batchUUID, QuestPath.Step s) {
        return switch (s.kind()) {
            case ROOM -> MCQuest.standalone(batchUUID, null, new ResourceLocation(s.id()));
            case JOB_CHANGE -> {
                MCQuest job = MCQuest.jobChange(batchUUID, null, pathJob(s.id()));
                job.setFlavorText("Right-click a villager and use Blocks of Progress");
                yield job;
            }
            case LOOT -> MCQuest.loot(batchUUID, null, new ResourceLocation(s.id()), s.count());
            case SKILL -> MCQuest.namedSkillUnlock(batchUUID, null, pathJob(s.id()));
            case LEVEL -> MCQuest.villagerLevel(batchUUID, null, s.id(), s.count());
        };
    }

    private static JobID pathJob(String rootSlashJob) {
        String[] parts = rootSlashJob.split("/");
        return new JobID(parts[0], parts[1]);
    }

    private boolean minerBeatPosted() {
        ResourceLocation minerJob = JobID.toRL(ONBOARDING_MINER_JOB);
        return questBatches.includes(q -> q.getType() == Quest.QuestType.JOB_CHANGE
                && minerJob.equals(q.getWantedId()));
    }

    void addMiner(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest minerJobQuest = MCQuest.jobChange(batchUUID, null, ONBOARDING_MINER_JOB);
        minerJobQuest.setFlavorText("Your town needs a miner. Teach a villager the miner job so coal stops being hand-fed.");
        q.addNewQuest(minerJobQuest);

        MCQuest pickaxeQuest = MCQuest.item(batchUUID, null, Compat.getItemId(Items.WOODEN_PICKAXE), 4);
        pickaxeQuest.setFlavorText("Craft 4 wooden pickaxes and place them in your store room chest. Each miner carries one.");
        q.addNewQuest(pickaxeQuest);

        // A LOOT quest: three coal, counted as the miner brings it back.
        MCQuest coalQuest = MCQuest.loot(batchUUID, null, Compat.getItemId(Items.COAL), 3);
        coalQuest.setFlavorText("Your miner will dig coal so the furnaces keep running. This completes once they have mined 3.");
        q.addNewQuest(coalQuest);

        MCQuest mineshaftQuest = MCQuest.standalone(batchUUID, null, view.getRoomForJob(new JobID("miner", "coal")));
        mineshaftQuest.setFlavorText("The miner needs a mineshaft to work in. Craft one at the town flag from stone stairs and place it near the flag.");
        q.addNewQuest(mineshaftQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_MINER));
        provisionArrival(view, qq);
        // Onboarding-flagged, so the job change gets the promised job.
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #2.5 (miner) was added to town: {}", qq.toNiceString());
    }

    /** After the cook: a room quest for the job that makes the town's most-needed item. Skipped when nothing can make it; one at a time. */
    private @Nullable Tutorial tryPhase2_75_topNeedUnlock(TutorialTownView view) {
        if (jobChangesComplete() < 2) {
            return Tutorial.SKIPPED;
        }
        if (!town.isInitialized()) {
            return null;
        }
        TownFlagBlockEntity t = town.getUnsafe();
        TownInterface ti = town.getUnsafe();
        NoMCEconomics econ = t.getEconomicsHandle();
        econ.update();
        ImmutableList<ItemEconomicsData> needs = econ.getAggregatedItems(null);
        if (needs.isEmpty()) {
            QT.QUESTS_LOGGER.info("Top-need batch: no unmet item needs registered; falling through");
            return null;
        }
        if (topNeedBeatOpen(questBatches.getAll())) {
            // The top-need room quest is still open: wait for it.
            return Tutorial.SKIPPED;
        }
        ServerLevel level = t.getServerLevel();
        Set<String> unlocked = unlockedRoots(ti);
        Set<String> excluded = new HashSet<>(unlocked);
        excluded.addAll(builtTopNeedProducers(questBatches.getAll()));
        TopNeedChoice choice = chooseTopNeed(
                needs,
                key -> ServerJobsRegistry.rootsThatCanMake(level, Ingredients.fromString(key)),
                excluded
        );
        if (choice.need() == null) {
            QT.QUESTS_LOGGER.info("Top-need batch: walked {} need(s); none actionable (unlocked or built {})",
                    needs.size(), excluded);
            return null;
        }
        ItemEconomicsData top = choice.need();
        String producer = choice.producer();
        QT.QUESTS_LOGGER.info("Top-need batch: chose need {} (x{}) -> producer {} (unlocked {})",
                top.ingredientKey(), top.timesNeeded(), producer, unlocked);
        JobID rootJob = ServerJobsRegistry.getAllRootJobs().stream()
                .filter(j -> producer.equals(j.rootId()))
                .findFirst()
                .orElse(new JobID(producer, producer));
        ResourceLocation room = view.getRoomForJob(rootJob);
        String itemName = Ingredients.getName(Ingredients.fromString(top.ingredientKey())).getString();

        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        MCQuest roomQuest = MCQuest.standalone(batchUUID, null, room);
        roomQuest.setTopNeedProducer(producer);
        roomQuest.setFlavorText("Your town keeps running out of " + itemName + ". A " + producer
                + " can make it. Build the " + producer + " room and register its door so one has a place to work.");
        q.addNewQuest(roomQuest);
        MCQuestBatch qb = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_NEW_JOB_ROOM));
        provisionArrival(view, qb);
        questBatches.add(qb);
        QT.QUESTS_LOGGER.info("Top-need batch posted: {} room {} (top need {})",
                producer, room, top.ingredientKey());
        notifyNewQuests(view);
        return Tutorial.APPLIED;
    }

    private int jobChangesComplete() {
        return (int) questBatches.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE && q.isComplete())
                .count();
    }

    private @Nullable Tutorial tryPhase3_newJobRoom(TutorialTownView view) {
        @Nullable JobHaver jobToCreateRoomFor = getJobToCreateRoomFor(view);
        if (jobToCreateRoomFor == null) {
            return null;
        }
        addQuestForNewJobRoom(view, jobToCreateRoomFor);
        notifyNewQuests(view);
        return Tutorial.APPLIED;
    }

    private @Nullable Tutorial tryPhase4_crafter(TutorialTownView view) {
        if (!phase4Started()) {
            view.fireTutorialTrigger(TutorialTrigger.Triggers.TutorialComplete);
            addPhase4CrafterQuests(view);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (!phase4Complete()) {
            return Tutorial.SKIPPED;
        }
        return null;
    }

    private @Nullable Tutorial tryPhase5_supplyChain(TutorialTownView view) {
        if (!phase5Started()) {
            addPhase5SupplyChainQuests(view);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (!phase5Complete()) {
            return Tutorial.SKIPPED;
        }
        return null;
    }



    private static final ResourceLocation CRAFTER_STICK_JOB = JobID.toRL(new JobID("crafter", "stick"));
    private static final ResourceLocation CRAFTER_BOWL_JOB = JobID.toRL(new JobID("crafter", "bowl"));
    private static final ResourceLocation BAKER_BREAD_JOB = JobID.toRL(new JobID("baker", "bread"));
    private static final ResourceLocation FARMER_JOB = JobID.toRL(new JobID("farmer", "wheat"));
    private static final ResourceLocation CRAFTING_ROOM = new ResourceLocation("questown", "crafting_room");
    private static final ResourceLocation KITCHEN_SMALL = new ResourceLocation("questown", "kitchen_small");
    private static final ResourceLocation BIG_BEDROOM = new ResourceLocation("questown", "big_bedroom");
    private boolean supplyChainsTriggerFired = false;
    private static final ResourceLocation STORE_ROOM_MEDIUM = new ResourceLocation("questown", "store_room_medium");


    private boolean phase4Started() {
        return questBatches.includes(q -> CRAFTER_STICK_JOB.equals(q.getWantedId()) && q.getType() == Quest.QuestType.JOB_CHANGE);
    }

    private boolean phase4Complete() {
        return questBatches.includes(q -> CRAFTER_BOWL_JOB.equals(q.getWantedId()) && q.getType() == Quest.QuestType.JOB_CHANGE && q.isComplete());
    }

    private boolean phase5Started() {
        return questBatches.includes(q -> q.getType() == Quest.QuestType.CONCURRENT_JOBS);
    }

    private boolean phase5Complete() {
        return questBatches.includes(q -> q.getType() == Quest.QuestType.CONCURRENT_JOBS && q.isComplete());
    }



    private void grantTutorialBop(TutorialTownView view) {
        if (view.tutorialBopGranted()) {
            return;
        }
        view.grantBop(3);
        view.markTutorialBopGranted();
        QT.QUESTS_LOGGER.info("Tutorial BOP grant: 3 BOPs added to flag");
    }

    private void notifyNewQuests(TutorialTownView view) {
        view.broadcastMessage("New quests are available! Right-click the town flag to see them.");
    }

    /** Marks a batch's quests as onboarding: they complete on any room that contains the wanted recipe. */
    private static void markOnboarding(MCQuestBatch batch) {
        for (MCQuest q : batch.getAll()) {
            q.setOnboarding(true);
        }
    }

    private void addPhase4CrafterQuests(TutorialTownView view) {
        // Batch A: Assign crafter + build crafting room
        UUID batchAUUID = UUID.randomUUID();
        MCQuestBatch.Inputs batchA = new MCQuestBatch.Inputs(batchAUUID, null);

        MCQuest crafterJobQuest = MCQuest.jobChange(batchAUUID, null, CRAFTER_STICK_JOB);
        crafterJobQuest.setFlavorText("Your village needs a crafter to automate production. Open a villager's skill tree and spend a Block of Progress to change their job.");
        batchA.addNewQuest(crafterJobQuest);

        MCQuest craftingRoomQuest = MCQuest.standalone(batchAUUID, null, CRAFTING_ROOM);
        craftingRoomQuest.setFlavorText("The crafter needs a workshop. Place a crafting table in a room and register the door with your wand.");
        batchA.addNewQuest(craftingRoomQuest);

        MCQuestBatch batchAQ = batchA.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_4A));
        markOnboarding(batchAQ);
        questBatches.add(batchAQ);

        // Batch B: Specialize crafter to bowl
        UUID batchBUUID = UUID.randomUUID();
        MCQuestBatch.Inputs batchB = new MCQuestBatch.Inputs(batchBUUID, null);

        MCQuest bowlJobQuest = MCQuest.jobChange(batchBUUID, null, CRAFTER_BOWL_JOB);
        bowlJobQuest.setFlavorText("Unlock Bowl Crafting on the skill tree. It costs another Block of Progress -- check the flag's BOP tab if you need more.");
        batchB.addNewQuest(bowlJobQuest);

        MCQuestBatch batchBQ = batchB.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_4B));
        markOnboarding(batchBQ);
        questBatches.add(batchBQ);

        QT.QUESTS_LOGGER.info("Tutorial phase 4 quests added (crafter + bowl)");
    }

    private void addPhase5SupplyChainQuests(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest concurrentQuest = MCQuest.concurrentJobs(batchUUID, ImmutableList.of(
                FARMER_JOB, BAKER_BREAD_JOB, CRAFTER_STICK_JOB
        ));
        concurrentQuest.setFlavorText("A thriving village needs many hands. Make sure your farmer, baker, and crafter are all working at the same time.");
        q.addNewQuest(concurrentQuest);

        MCQuest kitchenQuest = MCQuest.standalone(batchUUID, null, KITCHEN_SMALL);
        kitchenQuest.setFlavorText("Your baker needs a kitchen. Place a furnace in a room and register the door.");
        q.addNewQuest(kitchenQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_5));
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.info("Tutorial phase 5 quests added (supply chain)");
    }



    private record JobHaver(VillagerUUID villager, JobID job) {
    }

    private @Nullable JobHaver getJobToCreateRoomFor(TutorialTownView view) {
        JobHaver jobToUseIfNoQuestsExist = null;
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (!q.isComplete()) {
                    continue;
                }
                if (q.getType() != Quest.QuestType.JOB_CHANGE) {
                    continue;
                }
                if (batch.getOwner() == null) {
                    QT.logBug("Job Change quest had no owner.");
                    continue;
                }
                jobToUseIfNoQuestsExist = new JobHaver(
                        batch.getOwner(),
                        JobID.fromRL(q.getWantedId())
                );
                if (jobRoomQuestExists(view, q, batch.getOwner())) {
                    return null;
                }
            }
        }
        return jobToUseIfNoQuestsExist;
    }

    private boolean jobRoomQuestExists(
            TutorialTownView view,
            MCQuest jobChange,
            @NotNull VillagerUUID owner
    ) {
        JobID jobId = JobID.fromRL(jobChange.getWantedId());
        ResourceLocation room = view.getRoomForJob(jobId);
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            if (batchIsRoomForJob(batch, owner, room)) {
                return true;
            }
        }
        return false;
    }

    private boolean batchIsRoomForJob(
            MCQuestBatch batch,
            @NotNull VillagerUUID owner,
            ResourceLocation room
    ) {
        if (batch.size() != 1) {
            return false;
        }
        if (!owner.equals(batch.getOwner())) {
            return false;
        }
        return batch.getAll().stream().allMatch(v -> v.getWantedId().equals(room));
    }

    private MCQuest roomQuest(
            UUID batchUUID,
            ResourceLocation roomId
    ) {
        return MCQuest.standalone(batchUUID, null, roomId);
    }

    /** The onboarding cook's job: a registered cook job, the root of the cook's progression. */
    public static final JobID ONBOARDING_COOK_JOB = new JobID("cook", "extract");

    static final JobID ONBOARDING_MINER_JOB = new JobID("miner", "coal");

    void addHunterGatherer(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest rawFoodQuest = MCQuest.loot(
                batchUUID, null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), 1
        );
        rawFoodQuest.setFlavorText("Your villagers need food. A villager must bring back raw food.");
        q.addNewQuest(rawFoodQuest);

        MCQuest swordQuest = MCQuest.item(batchUUID, null, Compat.getItemId(Items.STONE_SWORD), 4);
        swordQuest.setFlavorText("Craft 4 stone swords and place them in your store room chest. Each hunter carries one.");
        q.addNewQuest(swordQuest);

        MCQuest hunterQuest = MCQuest.jobChange(batchUUID, null, new JobID("hunter", "sword"));
        hunterQuest.setFlavorText("Right-click a villager and use Blocks of Progress");
        q.addNewQuest(hunterQuest);

        MCQuest bedroomQuest = MCQuest.standalone(batchUUID, null, SpecialQuests.BEDROOM);
        q.addNewQuest(bedroomQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_HUNTER_GATHERER));
        // Onboarding-flagged, so the job change gets the promised job.
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #1.5 was added to town: {}", qq.toNiceString());
    }

    private boolean hunterRawFoodLootComplete() {
        ResourceLocation rawFood = TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location();
        return questBatches.getAll().stream().anyMatch(
                q -> q.getType() == Quest.QuestType.LOOT
                        && rawFood.equals(q.getWantedId())
                        && q.isComplete()
        );
    }

    private void addQuestsForJobChangeAndFood(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest cookJobQuest = MCQuest.jobChange(batchUUID, null, ONBOARDING_COOK_JOB);
        cookJobQuest.setFlavorText("Your town needs a cook. Teach a villager the cook job so raw food becomes a meal.");
        q.addNewQuest(cookJobQuest);

        // The cooked food, the cook skill and villager 3's bed come in the follow-up batch.

        MCQuest kitchenQuest = MCQuest.standalone(batchUUID, null, KITCHEN_SMALL);
        kitchenQuest.setFlavorText("The cook needs a kitchen. Place a furnace in a room and register the door.");
        q.addNewQuest(kitchenQuest);

        // The first storeroom upgrade comes here, as storage starts to fill.
        MCQuest storageUpgradeQuest = MCQuest.upgrade(batchUUID, null, SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM);
        storageUpgradeQuest.setFlavorText("Storage fills fast. Upgrade before production stops.");
        q.addNewQuest(storageUpgradeQuest);

        // The follow-up brings villager 3; this batch gives the Block of Progress the cook skill costs.
        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_COOK_JOB));
        // Onboarding-flagged.
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #2 was added to town: {}", qq.toNiceString());
    }

    /** Posts the cook follow-up (cooked food, a cook skill, villager 3's bed) once the cook job change is done and a villager is a cook. Runs every tick. */
    void postCookFollowUpIfDue(TutorialTownView view) {
        ResourceLocation cookJob = JobID.toRL(ONBOARDING_COOK_JOB);
        ResourceLocation cookedFood = TagsInit.Items.VILLAGER_COOKED_FOOD.location();
        List<MCQuest> all = questBatches.getAll();
        boolean cookUnlocked = all.stream().anyMatch(q -> q.isOnboarding()
                && q.getType() == Quest.QuestType.JOB_CHANGE
                && cookJob.equals(q.getWantedId())
                && q.isComplete());
        if (!cookUnlocked) {
            return;
        }
        if (!view.anyVillagerOnRoot(ONBOARDING_COOK_JOB.rootId())) {
            return;
        }
        boolean posted = all.stream().anyMatch(q -> q.isOnboarding()
                && q.getType() == Quest.QuestType.LOOT
                && cookedFood.equals(q.getWantedId()));
        if (posted) {
            return;
        }
        addCookFollowUpBatch(view);
        notifyNewQuests(view);
    }

    /** Five meals at 200 XP each reach level 1, so the quest ends on a Block of Progress. */
    static final int COOKED_FOOD_COUNT = 5;

    private void addCookFollowUpBatch(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        // Cooked food only.
        MCQuest cookedFoodQuest = MCQuest.loot(
                batchUUID, null, TagsInit.Items.VILLAGER_COOKED_FOOD.location(), COOKED_FOOD_COUNT
        );
        cookedFoodQuest.setFlavorText("Feed your growing village. Produce five pieces of food from the raw food you have gathered.");
        q.addNewQuest(cookedFoodQuest);

        // A skill on the cook's tree: a second recipe for the other meat the hunter brings.
        q.addNewQuest(MCQuest.skillUnlock(batchUUID, null, ONBOARDING_COOK_JOB.rootId()));

        // This batch brings villager 3, so the bedroom becomes a big bedroom.
        q.addNewQuest(MCQuest.upgrade(batchUUID, null, SpecialQuests.BEDROOM, BIG_BEDROOM));

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_JOB_CHANGE_FOOD));
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.info("Cook follow-up batch posted (cooked food + cook skill) on cook unlock");
    }

    private void addQuestForNewJobRoom(
            TutorialTownView view,
            JobHaver job
    ) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        ResourceLocation room = view.getRoomForJob(job.job());
        q.addNewQuest(MCQuest.standalone(batchUUID, job.villager(), room));

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_NEW_JOB_ROOM));
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #3 was added to town: {}", qq.toNiceString());
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
        if (isClaimedFor(room, q)) {
            return;
        }
        questBatches.markRecipeAsComplete(room, q);
    }

    /** Whether {@code room} is already used for a quest wanting {@code q}: a room completes only one quest per recipe. */
    boolean isClaimedFor(MCRoom room, ResourceLocation q) {
        if (SpecialQuests.EMPTY_ROOM.equals(q)) {
            return isClaimedByACompletedQuest(room);
        }
        return questBatches.getAll().stream()
                .filter(done -> q.equals(done.getWantedId()))
                .map(Quest::completedOn)
                .anyMatch(r -> r != null && r.yCoord == room.yCoord && r.doorPos.equals(room.doorPos));
    }

    /** An empty-room quest needs a room no completed quest has used. */
    boolean isClaimedByACompletedQuest(MCRoom room) {
        return questBatches.getAll().stream()
                .map(Quest::completedOn)
                .anyMatch(r -> r != null && r.yCoord == room.yCoord && r.doorPos.equals(room.doorPos));
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
        if (System.getenv("ENABLE_DEV_COMMANDS") != null) {
            QT.QUESTS_LOGGER.info("Quest completed: {} {}", quest.getType(), quest.getWantedId());
        }
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

    static boolean isProceduralBatch(QuestBatch<?, ?, ?, ?> batch) {
        return batch.getAll().stream().anyMatch(q -> {
            Object id = q.getWantedId();
            return !(id instanceof ResourceLocation rl) || !SpecialQuests.isSpecialQuestId(rl);
        });
    }

    @Override
    public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        town.getUnsafe().setChanged();
        String completionMessage = quest.getCompletionMessage();
        if (completionMessage != null && !completionMessage.isBlank()) {
            town.getUnsafe().messages.broadcastMessage(completionMessage);
        }
        if (isProceduralBatch(quest)) {
            fireChapterMilestonesIfNeeded();
        }
    }

    /** Chapter advancements at 5, 10 and 20 completed procedural batches. */
    private void fireChapterMilestonesIfNeeded() {
        TownFlagBlockEntity t = town.getUnsafe();
        t.completedProceduralBatches++;
        if (t.completedProceduralBatches == 5) {
            fireChapterTrigger(t, TutorialTrigger.Triggers.Chapter2);
        } else if (t.completedProceduralBatches == 10) {
            fireChapterTrigger(t, TutorialTrigger.Triggers.Chapter3);
        } else if (t.completedProceduralBatches == 20) {
            fireChapterTrigger(t, TutorialTrigger.Triggers.Chapter4);
        }
    }

    private static void fireChapterTrigger(TownFlagBlockEntity t, TutorialTrigger.Triggers trigger) {
        if (!(t.getLevel() instanceof ServerLevel sl)) {
            return;
        }
        AdvancementsInit.TUTORIAL_TRIGGER.triggerForNearestPlayer(sl, trigger, t.getBlockPos());
    }

    /** Whether a villager may arrive. In an onboarding town the second waits for the raw-food quest; the first never waits. */
    public boolean isArrivalGateOpen(long villagerCount) {
        if (villagerCount == 0) {
            return true;
        }
        if (getAll().stream().noneMatch(Quest::isOnboarding)) {
            return true;
        }
        return hunterRawFoodLootComplete();
    }

    public record ArrivalRelease<R>(List<R> others, List<R> arrivals, List<R> held) {
    }

    /** The change-job tab's red dot: a job-change quest is open and this villager is still a gatherer. */
    public boolean wantsJobChangeFrom(JobID villagerJob) {
        if (!GATHERER_ROOT.equals(villagerJob.rootId())) {
            return false;
        }
        return getAll().stream().anyMatch(
                q -> q.getType() == Quest.QuestType.JOB_CHANGE && !q.isComplete()
        );
    }

    /** The change-job tab is hidden from non-gatherers until the paths start. */
    public boolean hidesJobChangeFrom(JobID villagerJob) {
        if (GATHERER_ROOT.equals(villagerJob.rootId())) {
            return false;
        }
        return getAll().stream().anyMatch(Quest::isOnboarding) && !atTheFork();
    }

    private static final String GATHERER_ROOT = "gatherer";

    /** The campfire quest is open. */
    public boolean isCampfireQuestOpen() {
        return getAll().stream().anyMatch(
                q -> SpecialQuests.CAMPFIRE.equals(q.getWantedId()) && !q.isComplete()
        );
    }

    /** The campfire quest is done. */
    public boolean isCampfireQuestComplete() {
        return getAll().stream().anyMatch(
                q -> SpecialQuests.CAMPFIRE.equals(q.getWantedId()) && q.isComplete()
        );
    }

    /** Splits a morning's rewards: arrivals pass {@link #isArrivalGateOpen} one at a time; others always pass. */
    public <R> ArrivalRelease<R> releaseArrivals(
            List<R> queued,
            Predicate<R> isArrival,
            long villagerCount
    ) {
        List<R> others = new ArrayList<>();
        List<R> arrivals = new ArrayList<>();
        List<R> held = new ArrayList<>();
        long count = villagerCount;
        for (R r : queued) {
            if (!isArrival.test(r)) {
                others.add(r);
                continue;
            }
            if (!isArrivalGateOpen(count)) {
                held.add(r);
                continue;
            }
            arrivals.add(r);
            count++;
        }
        return new ArrivalRelease<>(others, arrivals, held);
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

    public Collection<MCQuest> getAllForVillager(VillagerUUID uuid) {
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
        runForTopMatch(this::recipesFromLevel, match, r -> {
            markQuestAsComplete(room, r);
            if (isWorkBlockRoom(r)) {
                AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                        l,
                        RoomTrigger.Triggers.FirstJobBlock,
                        Positions.ToBlock(room.getDoorPos(), room.yCoord)
                );
            }
        });
    }

    private static final ImmutableSet<ResourceLocation> NON_WORK_ROOMS = ImmutableSet.of(
            SpecialQuests.CAMPFIRE,
            SpecialQuests.BROKEN,
            SpecialQuests.TOWN_GATE,
            SpecialQuests.TOWN_FLAG,
            SpecialQuests.FARM,
            SpecialQuests.BEDROOM,
            SpecialQuests.STORE_ROOM_SMALL,
            SpecialQuests.JOB_BOARD,
            SpecialQuests.DINING_ROOM,
            SpecialQuests.CLINIC
    );

    private static boolean isWorkBlockRoom(ResourceLocation recipeId) {
        return !SpecialQuests.isSpecialQuest(recipeId) && !NON_WORK_ROOMS.contains(recipeId);
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

    public void processItemQuests(ImmutableList<MCTownItem> allStacks) {
        questBatches.processItemQuests(allStacks, this::questMatchesStack, TownQuests.TRACKER);
    }

    /** Progress of each open ITEM or LOOT quest: items in town storage for ITEM, items brought back for LOOT. */
    public Map<ResourceLocation, Integer> openQuestProgress() {
        List<MCQuest> open = questBatches.getAll().stream()
                .filter(q -> (q.getType() == Quest.QuestType.ITEM || q.getType() == Quest.QuestType.LOOT
                        || q.getType() == Quest.QuestType.VILLAGER_LEVEL)
                        && !q.isComplete())
                .toList();
        if (open.isEmpty()) {
            return ImmutableMap.of();
        }
        boolean anyItems = open.stream().anyMatch(q -> q.getType() == Quest.QuestType.ITEM);
        TownFlagBlockEntity t = this.town.getUnsafe();
        ImmutableList<MCTownItem> stacks = anyItems
                ? TownContainers.getAllStacks(t, t.getServerLevel())
                : ImmutableList.of();
        boolean anyLevels = open.stream().anyMatch(q -> q.getType() == Quest.QuestType.VILLAGER_LEVEL);
        Map<String, Integer> levels = anyLevels ? t.highestLevelByRoot() : Map.of();
        Map<ResourceLocation, Integer> out = new HashMap<>();
        for (MCQuest q : open) {
            if (q.getType() == Quest.QuestType.LOOT) {
                out.put(q.getWantedId(), q.getExtracted());
            } else if (q.getType() == Quest.QuestType.VILLAGER_LEVEL) {
                VillagerLevelQuests.target(q.getWantedId())
                        .ifPresent(l -> out.put(q.getWantedId(), levels.getOrDefault(l.root(), 0)));
            } else {
                out.put(q.getWantedId(), stacks.stream()
                        .filter(s -> questMatchesStack(q.getWantedId(), s))
                        .mapToInt(s -> s.toQTItemStack().getCount())
                        .sum());
            }
        }
        return out;
    }

    /** The board requests an open quest still needs, so they never pause. A villager-level quest needs them all. */
    public Predicate<WorkRequest> requestsOpenQuestsNeed() {
        List<MCQuest> open = questBatches.getAll().stream().filter(q -> !q.isComplete()).toList();
        if (open.stream().anyMatch(q -> q.getType() == Quest.QuestType.VILLAGER_LEVEL)) {
            return r -> true;
        }
        List<ItemStack> wanted = open.stream()
                .filter(q -> q.getType() == Quest.QuestType.ITEM || q.getType() == Quest.QuestType.LOOT)
                .flatMap(q -> Arrays.stream(Ingredients.asIngredient(q.getWantedId()).getItems()))
                .toList();
        return r -> {
            Ingredient requested = r.asIngredient();
            return wanted.stream().anyMatch(requested);
        };
    }

    /**
     * Completes open room and item quests the town already satisfies. Covers quests posted after
     * the room or items were already there.
     */
    private void backfillSatisfiedQuests(TownInterface town) {
        if (questBatches.isEmpty()) {
            return;
        }
        OpenQuests open = openQuests();
        if (open.levels()) {
            markVillagerLevelsReached(this.town.getUnsafe().highestLevelByRoot());
        }
        completeSatisfiedRoomQuests(town, open.rooms());
        if (open.items()) {
            processItemQuests(TownContainers.getAllStacks(this.town.getUnsafe(), town.getServerLevel()));
        }
    }

    private record OpenQuests(List<MCQuest> rooms, boolean items, boolean levels) {}

    private OpenQuests openQuests() {
        List<MCQuest> rooms = new ArrayList<>();
        boolean items = false;
        boolean levels = false;
        for (MCQuest q : questBatches.getAllBatches().stream().flatMap(QuestBatch::stream).toList()) {
            if (q.isComplete()) {
                continue;
            }
            switch (q.getType()) {
                case ROOM -> rooms.add(q);
                case ITEM -> items = true;
                case VILLAGER_LEVEL -> levels = true;
                default -> { }
            }
        }
        return new OpenQuests(rooms, items, levels);
    }

    private void completeSatisfiedRoomQuests(
            TownInterface town,
            List<MCQuest> openRooms
    ) {
        for (MCQuest q : openRooms) {
            Optional<MCRoom> eligible = town.getRoomHandle().getRoomsMatching(q.getWantedId()).stream()
                    .map(RoomRecipeMatch::getRoom)
                    .filter(r -> !isClaimedFor(r, q.getWantedId()))
                    .findFirst();
            if (eligible.isEmpty()) {
                logRoomQuestWaiting(q);
                continue;
            }
            markQuestAsComplete(eligible.get(), q.getWantedId());
        }
    }

    private static long lastRoomQuestWaitingLog = 0L;

    private static void logRoomQuestWaiting(MCQuest q) {
        if (System.getenv("ENABLE_DEV_COMMANDS") == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastRoomQuestWaitingLog <= 10_000_000_000L) {
            return;
        }
        lastRoomQuestWaitingLog = now;
        QT.QUESTS_LOGGER.debug("Room quest {} is still open: no registered room matches it yet", q.getWantedId());
    }

    public void processJobChanges(ImmutableMap<VillagerUUID, JobID> villagerJobs) {
        // TODO: Consider putting on QuestBatches and testing
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.JOB_CHANGE) {
                    continue;
                }
                if (q.isComplete()) {
                    continue;
                }
                JobID wantedJobId = JobID.fromRL(q.getWantedId());

                if (batch.getOwner() != null) {
                    if (wantedJobId.equals(villagerJobs.get(batch.getOwner()))) {
                        QT.QUESTS_LOGGER.debug("Job change quest already had owner");
                        batch.markRecipeAsComplete(null, q.getWantedId());
                        return;
                    }
                }

                List<VillagerUUID> matches = villagerJobs
                        .entrySet()
                        .stream()
                        .filter(v -> v.getValue().equals(wantedJobId))
                        .map(v -> v.getKey())
                        .toList();
                if (matches.isEmpty()) {
                    // A template job id is never registered; match on the root instead.
                    matches = villagerJobs
                            .entrySet()
                            .stream()
                            .filter(v -> v.getValue().sameRoot(wantedJobId))
                            .map(v -> v.getKey())
                            .toList();
                }
                if (matches.isEmpty()) {
                    continue;
                }
                VillagerUUID owner = Compat.shuffle(matches.iterator(), town.getServerLevelUnsafe())
                                           .get(0);
                QT.QUESTS_LOGGER.debug("Job change quest owner changed from null to {}", owner);
                batch.setOwner(owner);
                batch.markRecipeAsComplete(null, q.getWantedId());
            }
        }
    }

    /** While an onboarding job-change quest is open, a Block of Progress spend promises its job. The oldest one for this villager wins. */
    public Optional<JobID> onboardingJobChangeGuarantee(VillagerUUID target) {
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.JOB_CHANGE || !q.isOnboarding() || q.isComplete()) {
                    continue;
                }
                VillagerUUID owner = q.getOwner();
                if (owner != null && !owner.equals(target)) {
                    continue;
                }
                return Optional.of(JobID.fromRL(q.getWantedId()));
            }
        }
        return Optional.empty();
    }

    /** Completes the villager's job-change quest at the spend, if its job matches the decided one and it isn't another villager's. */
    public boolean markJobChangeCompleteAtSpend(
            VillagerUUID villager,
            JobID pendingRoot
    ) {
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.JOB_CHANGE || q.isComplete()) {
                    continue;
                }
                JobID wantedJobId = JobID.fromRL(q.getWantedId());
                boolean matches = wantedJobId.equals(pendingRoot) || wantedJobId.sameRoot(pendingRoot);
                if (!matches) {
                    continue;
                }
                VillagerUUID owner = q.getOwner();
                if (owner != null && !owner.equals(villager)) {
                    continue;
                }
                q.setOwner(villager);
                batch.setOwner(villager);
                batch.markRecipeAsComplete(null, q.getWantedId());
                QT.QUESTS_LOGGER.info(
                        "JOB_CHANGE quest {} completed at BOP spend for {} (pending root {})",
                        q.getWantedId(),
                        villager,
                        pendingRoot
                );
                return true;
            }
        }
        return false;
    }

    public void processConcurrentJobs(ImmutableMap<VillagerUUID, JobID> villagerJobs) {
        Set<ResourceLocation> activeJobRLs = villagerJobs.values().stream()
                                                          .map(JobID::toRL)
                                                          .collect(Collectors.toSet());
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.CONCURRENT_JOBS) {
                    continue;
                }
                if (q.isComplete()) {
                    continue;
                }
                Collection<ResourceLocation> required = q.getConcurrentJobIds();
                if (activeJobRLs.containsAll(required)) {
                    batch.markRecipeAsComplete(null, q.getWantedId());
                    return;
                }
            }
        }
    }

    private boolean questMatchesStack(
            ResourceLocation wantedId,
            MCTownItem stack
    ) {
        return Ingredients.asIngredient(wantedId).test(stack.toQTItemStack());
    }

    /** LOOT quests count items brought back: each matching extraction adds to every matching open quest, which completes at its count. */
    public void processLootQuests(MCTownItem extracted) {
        if (extracted == null || extracted.isEmpty()) {
            QT.QUESTS_LOGGER.info("[LOOTTRACE] processLootQuests: extracted is null/empty; skipping");
            return;
        }
        ItemStack stack = extracted.toQTItemStack();
        int lootQuestsSeen = 0;
        for (MCQuest quest : questBatches.getAll()) {
            if (quest.getType() != Quest.QuestType.LOOT || quest.isComplete()) {
                continue;
            }
            lootQuestsSeen++;
            boolean match = quest.getWantedId() != null
                    && Ingredients.asIngredient(quest.getWantedId()).test(stack);
            QT.QUESTS_LOGGER.info("[LOOTTRACE] extract '{}' vs LOOT quest {} -> {} (extracted {}/{})",
                    stack.getItem() != null ? stack.getItem().getName(stack).getString() : "?",
                    quest.getWantedId(), match, quest.getExtracted() + stack.getCount(), quest.getCountNeeded());
            if (match) {
                // Every matching quest gets the count.
                if (quest.addExtracted(stack.getCount()) >= quest.getCountNeeded()) {
                    questBatches.markRecipeAsComplete(null, quest.getWantedId());
                }
            }
        }
        if (lootQuestsSeen == 0) {
            QT.QUESTS_LOGGER.info("[LOOTTRACE] extracted '{}' but no open LOOT quests exist", stack);
        }
    }
}
