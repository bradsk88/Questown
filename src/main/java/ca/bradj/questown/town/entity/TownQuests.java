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
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.integration.minecraft.MCTownItem;
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
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.adapter.Positions;
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

    private final Stack<PendingReward> questRequests = new Stack<>();
    private final UnsafeTown town = new UnsafeTown(getClass());
    boolean playerDiscardedLastBatch;
    private @Nullable QuestBatchSeed pendingQuests = null;

    /** Root -> tick a townie of that root was first observed stuck in UNMET_ITEM. */
    private final Map<String, Long> unmetRootSince = new HashMap<>();

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
        MCRewardList reward = defaultQuestCompletionRewards(town);

        Collection<MCQuest> doneAndMaybeAlreadyUpgraded = quests.getAllForVillager(visitorUUID).stream()
                                                                .filter(Quest::isComplete).toList();
        List<MCQuest> doneAndReadyForFirstUpgrade = doneAndMaybeAlreadyUpgraded.stream()
                                                                               // TODO: Filter out recipes that have already been slated for upgrade?
                                                                               .filter(v -> v.fromRecipeID().isEmpty())
                                                                               .toList();
        Collection<MCQuest> villagerQuests = doneAndReadyForFirstUpgrade;

        // Prefer upgrading non-upgraded quests, but move up to the next tier if there are none left
        if (villagerQuests.isEmpty()) {
            villagerQuests = doneAndMaybeAlreadyUpgraded;
        }

        if (villagerQuests.isEmpty()) {
            QT.QUESTS_LOGGER.error(
                    "No upgrade paths could be determined because no quests have been completed yet for {}",
                    visitorUUID
            );
            QT.QUESTS_LOGGER.info("Skipping generation. The flag entity will generate a random batch instead.");
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
                  .map(v -> Arrays.stream(v.getItems()).map(ItemStack::getItem).map(Loader::itemIdOf)
                                  .filter(Objects::nonNull).map(ResourceLocation::toString).toList()).toList();
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

    private static boolean sameItem(
            ItemEconomicsData v,
            MCTownItem z
    ) {
        return Ingredients.fromString(v.ingredientKey()).test(z.toMCItemStack());
    }

    private static boolean producesFood(
            TownFlagBlockEntity town,
            JobID j
    ) {
        return ServerJobsRegistry.getResults(town.getTownData(), j).stream().anyMatch(
                i -> Ingredient.of(TagsInit.Items.VILLAGER_FOOD).test(i.toMCItemStack())
        );
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
        tickUnmetUnlockQuests(town);
        backfillSatisfiedQuests(town);

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
                town::hasEnoughBeds,
                () -> getNeededRooms(town.getEconomicsHandle()).stream().filter(v -> TownQuests.isNotSpecial(v.id()))
                                                               .toList(),
                () -> {
                    // 1.20.6: the recipe id lives on the holder, not the recipe
                    // object (RoomRecipe.getId() is null for registry recipes - this
                    // used to NPE the flag tick in QuestBatchSeed.getRandomRoom).
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
            questBatches.add(q);
            QT.QUESTS_LOGGER.debug("Precompiled quest batch was given to {}: {}", pr.owner(), q.toNiceString());
            playerDiscardedLastBatch = false;
            return;
        }

        pendingQuests = pop; // Can't grow more (at the moment) and not needed. Push back for next tick.
    }

    /**
     * Surfaces a stuck townie as the next quest. While a root has no viable job its townies
     * sit in UNMET_ITEM; past a grace period the town posts a JOB_UNLOCK quest naming the
     * missing item and the root that produces it. The quest completes when any townie unlocks
     * that root (see {@link #processJobUnlocks}) and is lost once no stuck root maps to it
     * any more (e.g. the player stocked the missing item directly).
     */
    void tickUnmetUnlockQuests(TownInterface town) {
        long now = Util.getTick(town.getServerLevel());
        Set<String> stuckRoots = new HashSet<>();
        Map<String, String> stuckRootToMissingItem = new HashMap<>();
        Map<String, VillagerUUID> stuckRootToTownie = new HashMap<>();
        for (LivingEntity entity : town.getVillagerHandle().entities()) {
            if (!(entity instanceof VisitorMobEntity v)) {
                continue;
            }
            if (v.getNeed() != TownieNeed.UNMET_ITEM || v.getUnmetItem().isEmpty()) {
                continue;
            }
            String root = v.getJobId().rootId();
            stuckRoots.add(root);
            stuckRootToMissingItem.putIfAbsent(root, v.getUnmetItem());
            stuckRootToTownie.putIfAbsent(root, VillagerUUID.from(v.getUUID()));
        }
        unmetRootSince.keySet().retainAll(stuckRoots);
        stuckRoots.forEach(r -> unmetRootSince.putIfAbsent(r, now));

        // The tutorial drives jobs through JOB_CHANGE quests; an unlock quest on top of
        // that is noise, so only post one once onboarding has stopped assigning jobs.
        boolean onboardingDriving = questBatches.includes(q -> !q.isComplete()
                && (q.isOnboarding() || q.getType() == Quest.QuestType.JOB_CHANGE));
        if (!onboardingDriving) {
            for (String root : stuckRoots) {
                if (now - unmetRootSince.get(root) < Config.UNMET_UNLOCK_QUEST_GRACE_TICKS.get()) {
                    continue;
                }
                createUnlockQuestIfAbsent(town, root, stuckRootToMissingItem.get(root), stuckRootToTownie.get(root));
            }
        }

        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.JOB_UNLOCK || q.isComplete()) {
                    continue;
                }
                String producer = JobID.fromRL(q.getWantedId()).rootId();
                boolean stillNeeded = stuckRootToMissingItem.entrySet().stream()
                        .anyMatch(e -> ServerJobsRegistry.staticProducerRootsForItem(
                                        town.getServerLevel(), Ingredients.fromString(e.getValue())).contains(producer));
                if (!stillNeeded) {
                    batch.markRecipeAsLostByKey(q.getWantedId());
                }
            }
        }
    }

    private void createUnlockQuestIfAbsent(
            TownInterface town,
            String root,
            String missingItemKey,
            VillagerUUID owner
    ) {
        TownFlagBlockEntity t = this.town.getUnsafe();
        Ingredient missing = Ingredients.fromString(missingItemKey);
        String producer = chooseProducerRoot(
                ServerJobsRegistry.staticProducerRootsForItem(t.getServerLevel(), missing), root, unlockedRoots(town)
        );
        if (producer == null) {
            return;
        }
        if (openJobUnlockQuestExists(producer)) {
            return;
        }
        JobID rootJob = ServerJobsRegistry.getAllRootJobs().stream()
                .filter(j -> producer.equals(j.rootId()))
                .findFirst()
                .orElse(new JobID(producer, producer));

        UUID batchUUID = UUID.randomUUID();
        MCQuest q = MCQuest.jobUnlock(batchUUID, owner, JobID.toRL(rootJob));
        q.setFlavorText("Your " + root + " townie cannot find work: "
                + Ingredients.getName(missing).getString() + " is missing from the town's supplies. "
                + "Unlock the " + producer + " job with a Block of Progress so the town can make it.");
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, owner);
        inputs.addNewQuest(q);
        MCReward reward = defaultQuestCompletionRewards(town);
        MCQuestBatch batch = inputs.withRewardUponCompletion(new MCDelayedReward(t, reward));
        questBatches.add(batch);
        QT.QUESTS_LOGGER.info("Stuck root {} (missing {}) -> JOB_UNLOCK quest for root {} posted to {}",
                root, missingItemKey, producer, owner);
        t.messages.broadcastMessage("New quests are available! Right-click the town flag to see them.");
    }

    private boolean openJobUnlockQuestExists(String producerRoot) {
        return questBatches.getAll().stream()
                           .anyMatch(q -> q.getType() == Quest.QuestType.JOB_UNLOCK
                                   && !q.isComplete()
                                   && producerRoot.equals(JobID.fromRL(q.getWantedId()).rootId()));
    }

    /**
     * The producer root a JOB_UNLOCK quest should target: the first candidate in sorted
     * order that is not the stuck root itself and not already unlocked by some townie
     * (an unlocked producer means the town already holds the capacity, so the stuck
     * state has a different cause and an unlock quest would not help).
     *
     * @param stuckRoot the root to exclude, or {@code null} for a town-wide need (the
     *                  top-need batch — it has no stuck root to exclude).
     */
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

    /**
     * The outcome of the top-need walk: the first need with an unlockable producer
     * (plus the root to unlock). Null {@code need()} means no ranked item was
     * actionable — the town already holds the capacity, or the supply is player input
     * — and the batch falls through to the fixed phases.
     */
    record TopNeedChoice(@Nullable ItemEconomicsData need, @Nullable String producer) {
    }

    /**
     * Walk the ranked unmet-need queue (most-needed first) to the first need a
     * not-yet-unlocked job can produce. Needs with no producer at all (e.g. player
     * inputs) or with only already-unlocked producers are <em>skipped</em>, not
     * dead-ends: run 14's #1 was exactly such a cell ({@code oak_wood}, zero
     * candidates) and it silently skipped the whole batch (gannet handoff,
     * decision 2).
     * <p>
     * The walk stops at the <em>first</em> actionable need; the caller checks whether
     * an unlock quest for its producer is already open and ends the phase — the walk
     * must not reach past an in-flight one to post for a lower-ranked need.
     */
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

    /**
     * Completes open JOB_UNLOCK quests once any townie has unlocked the wanted root.
     * Stateless, mirroring {@link #processJobChanges}: safe to run every flag tick.
     */
    public void processJobUnlocks(ImmutableMap<VillagerUUID, ImmutableSet<JobID>> unlockedJobs) {
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.JOB_UNLOCK || q.isComplete()) {
                    continue;
                }
                String wantedRoot = JobID.fromRL(q.getWantedId()).rootId();
                boolean nowUnlocked = rootNowUnlocked(wantedRoot, unlockedJobs);
                if (nowUnlocked) {
                    QT.QUESTS_LOGGER.info("JOB_UNLOCK quest for root {} complete: the root is now unlocked", wantedRoot);
                    batch.markRecipeAsComplete(null, q.getWantedId());
                    return;
                }
            }
        }
    }

    /** Any townie holding a work under the wanted root counts as the root being unlocked. */
    static boolean rootNowUnlocked(
            String wantedRoot,
            ImmutableMap<VillagerUUID, ImmutableSet<JobID>> unlockedJobs
    ) {
        for (ImmutableSet<JobID> jobs : unlockedJobs.values()) {
            for (JobID j : jobs) {
                if (wantedRoot.equals(j.rootId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isWaitingForHunterGathererTutorial() {
        if (questBatches.getAllBatches().size() > 3) {
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
        r = tryPhase1_campfireToKickoff(view);       if (r != null) return r;
        r = tryPhase2_hunterGatherer(view);           if (r != null) return r;
        r = tryPhase2_5_jobChangeAndFood(view);       if (r != null) return r;
        r = tryPhase2_75_topNeedUnlock(view);         if (r != null) return r;
        r = tryPhase3_newJobRoom(view);               if (r != null) return r;
        r = tryPhase4_crafter(view);                  if (r != null) return r;
        r = tryPhase5_supplyChain(view);              if (r != null) return r;
        r = tryPhase6_storageUpgrade(view);           if (r != null) return r;
        r = tryPhase7_exoticWood(view);               if (r != null) return r;
        return Tutorial.DONE;
    }

    private @Nullable Tutorial tryPhase1_campfireToKickoff(TutorialTownView view) {
        if (questBatches.hasOneQuestOnly(SpecialQuests.CAMPFIRE::equals)) {
            addQuestsForVillagerKickoff(view);
            view.broadcastTutorialToast(
                    "tutorial.visitor_arrived.title",
                    "tutorial.visitor_arrived.description"
            );
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (!allVillagerKickoffQuestsDone()) {
            return Tutorial.SKIPPED;
        }
        return null;
    }

    private @Nullable Tutorial tryPhase2_hunterGatherer(TutorialTownView view) {
        if (view.villagerCount() < 2) {
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

    private @Nullable Tutorial tryPhase2_5_jobChangeAndFood(TutorialTownView view) {
        if (view.villagerCount() < 3) {
            return Tutorial.SKIPPED;
        }
        if (questBatches.getAll().stream().filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE).count() < 2) {
            if (!hunterRawFoodLootComplete()) {
                return Tutorial.SKIPPED;
            }
            addQuestsForJobChangeAndFood(view);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (questBatches.getAll().stream().filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE && q.isComplete()).count() < 2) {
            return Tutorial.SKIPPED;
        }
        return null;
    }

    /**
     * The emergent batch after the cook lands: names the town's most-requested
     * unmet supply (ranked from the economics need queue) and posts "unlock the
     * job that produces it" + "build that job's base room". The content is
     * need-driven, not scripted: whichever root produces the top need wins
     * (coal -> miner today), and if nothing can produce the top supply the batch
     * is skipped and the fixed phases continue. Posted once per top need: an
     * already-open JOB_UNLOCK for the producer, or the producer being unlocked
     * by some townie, ends this phase.
     */
    private @Nullable Tutorial tryPhase2_75_topNeedUnlock(TutorialTownView view) {
        if (jobChangesComplete() < 2) {
            // Two completed job changes (hunter + cook) = "after cook".
            return Tutorial.SKIPPED;
        }
        if (!town.isInitialized()) {
            // No town state to read from; nothing to name yet. Continue the fixed phases.
            return null;
        }
        TownFlagBlockEntity t = town.getUnsafe();
        TownInterface ti = town.getUnsafe();
        NoMCEconomics econ = t.getEconomicsHandle();
        econ.update();
        ImmutableList<ItemEconomicsData> needs = econ.getAggregatedItems(null);
        if (needs.isEmpty()) {
            // No unmet supply registered yet; nothing to name. Continue the fixed phases.
            QT.QUESTS_LOGGER.info("Top-need batch: no unmet item needs registered; falling through");
            return null;
        }
        ServerLevel level = t.getServerLevel();
        Set<String> unlocked = unlockedRoots(ti);
        TopNeedChoice choice = chooseTopNeed(
                needs,
                key -> ServerJobsRegistry.staticProducerRootsForItem(level, Ingredients.fromString(key)),
                unlocked
        );
        if (choice.need() == null) {
            // Nothing in the ranking has a producer the town could unlock (player input
            // or already-unlocked capacity); nothing to name. Continue the fixed phases.
            QT.QUESTS_LOGGER.info("Top-need batch: walked {} need(s); none actionable (unlocked {})",
                    needs.size(), unlocked);
            return null;
        }
        ItemEconomicsData top = choice.need();
        String producer = choice.producer();
        QT.QUESTS_LOGGER.info("Top-need batch: chose need {} (x{}) -> producer {} (unlocked {})",
                top.ingredientKey(), top.timesNeeded(), producer, unlocked);
        if (openJobUnlockQuestExists(producer)) {
            // The top actionable need already has an unlock in flight: the phase's
            // purpose is served — end it here rather than walking deeper.
            return Tutorial.SKIPPED;
        }

        JobID rootJob = ServerJobsRegistry.getAllRootJobs().stream()
                .filter(j -> producer.equals(j.rootId()))
                .findFirst()
                .orElse(new JobID(producer, producer));
        ResourceLocation room = view.getRoomForJob(rootJob);
        String itemName = Ingredients.getName(Ingredients.fromString(top.ingredientKey())).getString();

        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        MCQuest unlockQuest = MCQuest.jobUnlock(batchUUID, null, JobID.toRL(rootJob));
        unlockQuest.setFlavorText("Your town's stock of " + itemName + " has run dry and no one can make it. "
                + "Unlock the " + producer + " job with a Block of Progress so the town can produce it.");
        q.addNewQuest(unlockQuest);
        MCQuest roomQuest = MCQuest.standalone(batchUUID, null, room);
        roomQuest.setFlavorText("The " + producer + " needs a room to work in. Build the "
                + producer + " room (" + room.getPath().replaceFirst(".*block\\.", "") + ") and register its door.");
        q.addNewQuest(roomQuest);
        MCQuestBatch qb = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_NEW_JOB_ROOM));
        questBatches.add(qb);
        QT.QUESTS_LOGGER.info("Top-need batch posted: unlock {} + room {} (top need {})",
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

    private @Nullable Tutorial tryPhase6_storageUpgrade(TutorialTownView view) {
        if (!phase6Started()) {
            view.fireTutorialTrigger(TutorialTrigger.Triggers.SecondJobType);
            addPhase6StorageUpgradeQuest(view);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (!phase6Complete()) {
            return Tutorial.SKIPPED;
        }
        return null;
    }

    private @Nullable Tutorial tryPhase7_exoticWood(TutorialTownView view) {
        if (!phase7Started()) {
            view.fireTutorialTrigger(TutorialTrigger.Triggers.FirstRoomUpgrade);
            addPhase7ExoticWoodQuest(view);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (!phase7Complete()) {
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
    private static final ResourceLocation STORE_ROOM_MEDIUM = new ResourceLocation("questown", "store_room_medium");

    static final String PHASE_7_FLAVOR_KEYWORD = "flagpole";

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

    private boolean phase6Started() {
        return questBatches.includes(q -> STORE_ROOM_MEDIUM.equals(q.getWantedId()) && q.getType() == Quest.QuestType.ROOM);
    }

    private boolean phase6Complete() {
        return questBatches.includes(q -> STORE_ROOM_MEDIUM.equals(q.getWantedId()) && q.getType() == Quest.QuestType.ROOM && q.isComplete());
    }

    private boolean phase7Started() {
        return questBatches.includes(q -> q.getType() == Quest.QuestType.ITEM && q.getFlavorText() != null && q.getFlavorText().contains(PHASE_7_FLAVOR_KEYWORD));
    }

    private boolean phase7Complete() {
        return questBatches.includes(q -> q.getType() == Quest.QuestType.ITEM && q.getFlavorText() != null && q.getFlavorText().contains(PHASE_7_FLAVOR_KEYWORD) && q.isComplete());
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

    /**
     * Flags a batch's quests as onboarding so they complete on a room that is a superset of the
     * wanted recipe ("any storeroom"), unlike random/procedural quests which require an exact recipe
     * match. See .scratch/storeroom-quest-completion.
     */
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

    private void addPhase6StorageUpgradeQuest(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest upgradeQuest = MCQuest.upgrade(batchUUID, null, SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM);
        upgradeQuest.setFlavorText("Storage fills fast. Upgrade before production stops.");
        q.addNewQuest(upgradeQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_6));
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.info("Tutorial phase 6 quest added (storage upgrade)");
    }

    private void addPhase7ExoticWoodQuest(TutorialTownView view) {
        ResourceLocation exoticWood = view.getExoticWood();
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest woodQuest = MCQuest.item(batchUUID, null, exoticWood, 1);
        String woodName = exoticWood.getPath().replace("_", " ");
        woodQuest.setFlavorText("Your village deserves a proper " + PHASE_7_FLAVOR_KEYWORD + ". Bring back " + woodName + " from the lands beyond.");
        q.addNewQuest(woodQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_7));
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.info("Tutorial phase 7 quest added (exotic wood: {})", exoticWood);
    }

    private boolean allVillagerKickoffQuestsDone() {
        return VILLAGER_KICKOFF_QUESTS.stream().allMatch(k -> questBatches.includes(q -> isDoneRoom(q, k)));
    }

    private static boolean isDoneRoom(
            MCQuest v,
            ResourceLocation bedroom
    ) {
        return v.isComplete() && v.getType() == Quest.QuestType.ROOM && bedroom.equals(v.getWantedId());
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

    static final ImmutableList<ResourceLocation> VILLAGER_KICKOFF_QUESTS = ImmutableList.of(
            SpecialQuests.TOWN_GATE,
            SpecialQuests.JOB_BOARD,
            SpecialQuests.STORE_ROOM_SMALL
    );

    private void addQuestsForVillagerKickoff(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest jobBoardQuest = roomQuest(batchUUID, SpecialQuests.JOB_BOARD);
        jobBoardQuest.setFlavorText("Place a wooden sign in a registered room. It will become a Job Board — this is how villagers find work.");
        q.addNewQuest(jobBoardQuest);

        MCQuest storeRoomQuest = roomQuest(batchUUID, SpecialQuests.STORE_ROOM_SMALL);
        storeRoomQuest.setFlavorText("Place a chest in a room and register the door. Villagers store their work here.");
        q.addNewQuest(storeRoomQuest);

        MCQuest gateQuest = roomQuest(batchUUID, SpecialQuests.TOWN_GATE);
        gateQuest.setFlavorText("Build a fence gate entrance for your town. Villagers need a way in.");
        q.addNewQuest(gateQuest);

        MCQuest swordQuest = MCQuest.item(batchUUID, null, Compat.getItemId(Items.WOODEN_SWORD), 4);
        swordQuest.setFlavorText("Craft 4 wooden swords and place them in your store room chest. Your villagers will need them - each hunter carries one.");
        q.addNewQuest(swordQuest);

        MCQuestBatch qb = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_KICKOFF));
        markOnboarding(qb);
        questBatches.add(qb);
        QT.QUESTS_LOGGER.debug("Tutorial quests added to town: {}", qb.toNiceString());
    }

    private MCQuest roomQuest(
            UUID batchUUID,
            ResourceLocation roomId
    ) {
        return MCQuest.standalone(batchUUID, null, roomId);
    }

    private void addHunterGatherer(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest rawFoodQuest = MCQuest.loot(
                batchUUID, null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), 1
        );
        rawFoodQuest.setFlavorText("Your villagers need food. A villager must bring back raw food.");
        q.addNewQuest(rawFoodQuest);

        MCQuest swordQuest = MCQuest.item(batchUUID, null, Compat.getItemId(Items.STONE_SWORD), 1);
        swordQuest.setFlavorText("Upgrade your villager's weapon. Craft a stone sword and place it in a store room chest.");
        q.addNewQuest(swordQuest);

        MCQuest hunterQuest = MCQuest.jobChange(batchUUID, null, new JobID("hunter", "sword"));
        hunterQuest.setFlavorText("Right-click a villager and use Blocks of Progress");
        q.addNewQuest(hunterQuest);

        MCQuest bedroomQuest = MCQuest.standalone(batchUUID, null, SpecialQuests.BEDROOM);
        bedroomQuest.setFlavorText("Place a bed in a room and register the door. Villagers need rest.");
        q.addNewQuest(bedroomQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_HUNTER_GATHERER));
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

        MCQuest cookJobQuest = MCQuest.jobChange(batchUUID, null, new JobID("cook", "simple_furnace_food"));
        cookJobQuest.setFlavorText("Your town needs a cook. Teach a villager the cook job so raw food becomes a meal.");
        q.addNewQuest(cookJobQuest);

        MCQuest cookedFoodQuest = MCQuest.loot(
                batchUUID, null, TagsInit.Items.VILLAGER_FOOD.location(), 8
        );
        cookedFoodQuest.setFlavorText("Feed your growing village. Produce eight pieces of food from the raw food you have gathered.");
        q.addNewQuest(cookedFoodQuest);

        MCQuest kitchenQuest = MCQuest.standalone(batchUUID, null, KITCHEN_SMALL);
        kitchenQuest.setFlavorText("The cook needs a kitchen. Place a furnace in a room and register the door.");
        q.addNewQuest(kitchenQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_JOB_CHANGE_FOOD));
        // Onboarding-flow quest: like the other tutorial-phase batches, it is
        // onboarding-flagged (see `.scratch/cook-skill-unlock/cook-skill-unlock.md`). That
        // flag is the discriminator the onboarding-cook starter grant keys on — when the
        // executor drives this job change, it marks `ChangeJobReward.onboarding(true)`,
        // and the applier then unlocks `cook/mutton` on top of the universal starter set
        // (see ChangeJobReward.getApplier). A random/procedural cook quest stays
        // unmarked and its cook gets only the universal set, leaving recipe jobs
        // gated on the job tree. Its room quest completes on a superset room per the
        // storeroom design (.scratch/storeroom-quest-completion).
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #2 was added to town: {}", qq.toNiceString());
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
        if (System.getenv("ENABLE_DEV_COMMANDS") != null) {
            QT.QUESTS_LOGGER.warn("[DEV-DIAG:quest-done] {} {}", quest.getType(), quest.getWantedId());
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

    private void fireChapterMilestonesIfNeeded() {
        TownFlagBlockEntity t = town.getUnsafe();
        t.completedProceduralBatches++;
        if (t.completedProceduralBatches == 1) {
            t.messages.broadcastMessage("messages.tutorial.complete");
        } else if (t.completedProceduralBatches == 5) {
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

    /**
     * Completes open ROOM and ITEM quests that the current town state already satisfies (a
     * registered room for the recipe, or enough item copies in town containers).
     *
     * <p>Quest completion is otherwise event-driven at satisfaction time: a room recipe fires a
     * one-shot {@code roomRecipeCreated} on registration, and item counts are tallied only on
     * stored-data changes. When a quest is added <em>after</em> the satisfying state already
     * exists (the tutorial issues the kickoff quests after the rooms and swords were placed),
     * that one-shot event never fires for them and they stall ACTIVE forever. Re-evaluating open
     * quests against current state each tick closes the ordering gap; the early-out makes the
     * steady state (no open room/item quests) a single list scan.
     */
    private void backfillSatisfiedQuests(TownInterface town) {
        ServerLevel level = town.getServerLevel();
        if (questBatches.isEmpty()) {
            return;
        }
        // Walk the journal LAZILY (no full copy): collect only the open ROOM
        // matches (usually zero in steady state) and test for an open ITEM
        // quest. This was questBatches.getAll() — a per-tick O(journal) copy of
        // EVERY quest — and the journal is unbounded (completed batches are never
        // pruned), so the steady-state cost was a growing flat copy every tick.
        final boolean[] openItems = {false};
        List<MCQuest> openRooms = new ArrayList<>();
        questBatches.getAllBatches().stream()
            .flatMap(b -> QuestBatch.stream(b))
            .forEach(q -> {
                if (q.isComplete()) {
                    return;
                }
                switch (q.getType()) {
                    case ROOM -> openRooms.add(q);
                    case ITEM -> openItems[0] = true;
                    default -> { }
                }
            });
        if (openRooms.isEmpty() && !openItems[0]) {
            return;
        }
        for (MCQuest q : openRooms) {
            Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomHandle().getRoomsMatching(q.getWantedId());
            if (!rooms.isEmpty()) {
                markQuestAsComplete(rooms.iterator().next().getRoom(), q.getWantedId());
            } else {
                probeNoRoomMatch(q);
            }
        }
        if (openItems[0]) {
            processItemQuests(TownContainers.getAllStacks(this.town.getUnsafe(), level));
        }
    }
    // DEV-DIAG backfill probe, 2026-10-02 — gated on ENABLE_DEV_COMMANDS; grep 'DEV-DIAG' to enumerate; remove when the soft-lock is confirmed fixed
    private static final Object BackfillProbeLock = new Object();
    private static long BackfillProbeLastNanos = 0L;
    private void probeNoRoomMatch(MCQuest q) {
        if (System.getenv("ENABLE_DEV_COMMANDS") == null) {
            return;
        }
        synchronized (BackfillProbeLock) {
            long now = System.nanoTime();
            if (now - BackfillProbeLastNanos <= 10_000_000_000L) {
                return;
            }
            BackfillProbeLastNanos = now;
        }
        QT.QUESTS_LOGGER.warn("[DEV-DIAG:backfill] open ROOM quest {} has no matching registered room", q.getWantedId());
    }
    // === end DEV-DIAG backfill probe ===

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
                    // Fallback: the job file "source" job (e.g. cook/simple_furnace_food) is
                    // expanded by ResourceJobLoader into per-item sub-jobs (cook/mutton, cook/beef, …).
                    // The source JobID itself is never registered, so exact-match never fires for
                    // it. Try matching on the job role (rootId) so the quest still completes when
                    // a townie holds any job in the same family.
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
        // asIngredient resolves item ids to item ingredients and tag ids to tag
        // ingredients, so ITEM quests with a tag want now match their tag members.
        return Ingredients.asIngredient(wantedId).test(stack.toQTItemStack());
    }

    /**
     * LOOT quests complete on production: when a townie extracts loot matching an open
     * LOOT quest, the first match completes it (no accumulation). The extracted item is
     * not consumed here; the normal deposit / eat path decides what happens to it.
     */
    public void processLootQuests(MCTownItem extracted) {
        // DEV-DIAG: raw_food hunt investigation, 2026-10-02 — the [LOOTTRACE] log calls below are diagnostic; grep 'DEV-DIAG' to enumerate; keep/remove per handoff
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
            QT.QUESTS_LOGGER.info("[LOOTTRACE] extract '{}' vs LOOT quest {} -> {}",
                    stack.getItem() != null ? stack.getItem().getName(stack).getString() : "?",
                    quest.getWantedId(), match);
            if (match) {
                questBatches.markRecipeAsComplete(null, quest.getWantedId());
                return;
            }
        }
        if (lootQuestsSeen == 0) {
            QT.QUESTS_LOGGER.info("[LOOTTRACE] extracted '{}' but no open LOOT quests exist", stack);
        }
    }
}
