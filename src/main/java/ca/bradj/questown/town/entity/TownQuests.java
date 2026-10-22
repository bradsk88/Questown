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

    /**
     * Whether any of the current quest batches still holds an incomplete quest (the G1
     * "!" attention bubble above the flag). Completed quests stay in their batch marked
     * complete until the batch is declined, so the check is per-quest, not per-batch.
     */
    public boolean hasIncompleteQuests() {
        return questBatches.getAll().stream().anyMatch(q -> !q.isComplete());
    }

    private final Stack<PendingReward> questRequests = new Stack<>();
    private final UnsafeTown town = new UnsafeTown(getClass());
    boolean playerDiscardedLastBatch;
    private @Nullable QuestBatchSeed pendingQuests = null;

    TownQuests() {
        questBatches.addChangeListener(this);
    }

    /**
     * The day-one quest batches, added when the flag is placed.
     *
     * <p>Two batches, in the order the chicken's arc asks for them
     * (.scratch/mat-gated-arrival): the campfire batch first — its reward is
     * the "your camp is ready" toast (the {@code first_campfire_sleep}
     * advancement already fires on the actual campfire sleep that night; a
     * spawn is deliberately NOT here — arrival is gated on the welcome mat);
     * and the gate batch — the TOWN_GATE quest, which completes when a welcome
     * mat is registered with the flag ({@code registerWelcomeMat}). Its reward
     * is the first villager at the next morning tick PLUS the empty-room batch
     * (D#2): the player learns "a room with a registered door" before the
     * basics batch (job board + store room) lands, which now posts when the
     * empty-room quest completes — so specific rooms are never demanded before
     * room-building is taught. On the chicken-assisted path the chicken builds
     * the gate room (walls + registered door) before the mat, so the
     * empty-room quest completes immediately on the tick after the mat posts
     * the batch; on the no-chicken path the player builds a small room and
     * wands the door.
     */
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

    /**
     * The D#2 "empty room" batch: a single ROOM quest that matches the
     * zero-ingredient {@code empty_room} room recipe, so it completes on any
     * room with a registered door. Posted by the welcome-mat gate reward;
     * its reward is the basics batch (job board + store room).
     */
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
        QT.QUESTS_LOGGER.info("Empty-room batch posted (onboarding beat between gate and basics)");
    }

    /**
     * The "basics" batch the welcome-mat reward posts (job board + store room).
     * Town-owned; completes on a superset room like the other onboarding
     * quests. Its reward is the old PHASE_KICKOFF reward (second villager at
     * the next morning + a batch request), which drives the tutorial phase
     * machine into the hunter-gatherer batch.
     */
    void addBasicsBatch() {
        TownFlagBlockEntity t = this.town.getUnsafe();
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest jobBoardQuest = roomQuest(batchUUID, SpecialQuests.JOB_BOARD);
        // No flavor text: the card tooltip is the two-line mat-format key
        // menu.quests.roomblock.job_board (click-to-craft, 2026-10-14 playtest).
        q.addNewQuest(jobBoardQuest);

        MCQuest storeRoomQuest = roomQuest(batchUUID, SpecialQuests.STORE_ROOM_SMALL);
        storeRoomQuest.setFlavorText("Place a chest in a room with a wand-registered door.");
        q.addNewQuest(storeRoomQuest);

        MCQuestBatch qb = q.withRewardUponCompletion(
                new MCRewardList(
                        t,
                        TownFlagTutorialAdapter.from(t, questBatches).makeReward(TutorialTownView.PHASE_KICKOFF),
                        // F2 day-2 beat: once the basics land, the job board needs its first
                        // request. The quest posts here (not on a morning tick) so the board
                        // exists by the time it is posted.
                        new MCInstantReward(t, new PostFirstWorkRequestQuestReward(t))
                ));
        markOnboarding(qb);
        questBatches.add(qb);
        t.messages.broadcastMessage("New quests are available! Right-click the town flag to see them.");
        QT.QUESTS_LOGGER.info("Basics batch posted (job board + store room) on welcome-mat completion");
    }

    /**
     * The day-2 "post your first work request" quest (F2). A single standalone quest in its
     * own batch; completes from {@link #markFirstWorkRequestCompleteIfActive} when the player
     * posts a request while it is active ({@code TownFlagBlockEntity.requestWorkFromPlayer}).
     * Auto-posted starter requests do not count (dev, 2026-10-22). Reward: one Block
     * of Progress - small, and it is the currency the board's other beats run on.
     */
    void addFirstWorkRequestBatch() {
        TownFlagBlockEntity t = this.town.getUnsafe();
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        q.addNewQuest(MCQuest.firstWorkRequest(batchUUID, null));
        MCQuestBatch qb = q.withRewardUponCompletion(
                new MCInstantReward(t, new GrantBlockOfProgressReward(t, 1)));
        questBatches.add(qb);
        // No broadcast of its own: the morning's "New quests are available!" covers it
        // (playtest 2026-10-17 round 6 - two back-to-back quest announcements).
        QT.QUESTS_LOGGER.info("First-work-request batch posted (day-2 board introduction)");
    }

    /**
     * Completes the active day-2 "first work request" quest when the player posts a request
     * ({@code TownFlagBlockEntity.requestWorkFromPlayer}). No-op when the quest is absent or
     * already complete.
     */
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

        // The holder owns the id in 1.20.2+; the seam pair carries both. (The recipe
        // object's getId() is null for every codec-constructed recipe — the old
        // raw-list loop NPE'd inside ImmutableList.Builder.add on the first match.)
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
            q.getAll().forEach(quest -> quest.setDiscardable(true));
            questBatches.add(q);
            QT.QUESTS_LOGGER.debug("Precompiled quest batch was given to {}: {}", pr.owner(), q.toNiceString());
            playerDiscardedLastBatch = false;
            return;
        }

        pendingQuests = pop; // Can't grow more (at the moment) and not needed. Push back for next tick.
    }

    /**
     * Withdraw every open JOB_UNLOCK quest. They told the player to unlock a job with a Block of
     * Progress, which rolls a random root, so the spend rarely delivered the job (dev, 2026-10-22:
     * post-onboarding quests are room quests only). New ones are no longer posted; this drains the
     * ones a saved town still carries. A top-need batch keeps its room quest, tagged with the
     * producer so the top-need beat still sees it as in flight.
     */
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

    /** The top-need beat is in flight: one of its room quests is still open. */
    static boolean topNeedBeatOpen(Collection<? extends Quest<?, ?>> quests) {
        return quests.stream().anyMatch(q -> !q.isComplete() && q.topNeedProducer().isPresent());
    }

    /**
     * Producers whose top-need room the town already built. Building the room does not staff it
     * (no unlock quest any more), so the need can stay on top; without this the beat would post
     * the same room again.
     */
    static Set<String> builtTopNeedProducers(Collection<? extends Quest<?, ?>> quests) {
        Set<String> built = new HashSet<>();
        quests.stream()
              .filter(Quest::isComplete)
              .forEach(q -> q.topNeedProducer().ifPresent(built::add));
        return built;
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

    public boolean isWaitingForHunterGathererTutorial() {
        // Calibrated to the day-one batch layout: the early tutorial has at most
        // four batches (campfire + gate + basics + this hunter-gatherer batch;
        // the old single-kickoff layout had three). Once the town moves past the
        // tutorial and visitor batches start piling up, the overnight hunter
        // override must stop dictating BOP-driven job changes.
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
        r = tryPhase2_75_topNeedUnlock(view);         if (r != null) return r;
        r = tryPhase3_newJobRoom(view);               if (r != null) return r;
        r = tryPhase4_crafter(view);                  if (r != null) return r;
        r = tryPhase5_supplyChain(view);              if (r != null) return r;
        // The supply chain was the last scripted beat once phase 7 (exotic wood) was cut (dev,
        // 2026-10-19). Its journal entry (entries/014-supply-chains) opened when phase 7 started.
        if (!supplyChainsTriggerFired) {
            view.fireTutorialTrigger(TutorialTrigger.Triggers.SecondJobType);
            supplyChainsTriggerFired = true;
        }
        return Tutorial.DONE;
    }

    private @Nullable Tutorial tryPhase2_hunterGatherer(TutorialTownView view) {
        // One villager: this batch posts the raw-food quest, and the second villager is
        // held until that quest completes (isArrivalGateOpen) - waiting for two deadlocks.
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

    /**
     * Two "empty room" quests between the hunter-gatherer batch and the cook batch (dev, 2026-10-19
     * playtest). Posted once the hunter-gatherer batch is fully done; the cook batch waits for it.
     * A town whose cook batch is already out (an existing world) skips it.
     */
    private @Nullable Tutorial tryPhase2_4_emptyRooms(TutorialTownView view) {
        if (cookBatchPosted()) {
            return null;
        }
        if (!hunterGathererBatchComplete()) {
            return Tutorial.SKIPPED;
        }
        if (!emptyRoomsBatchPosted()) {
            addEmptyRoomsBatch(view);
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

    private @Nullable MCQuestBatch emptyRoomsBatch() {
        return questBatches.getAllBatches().stream()
                .filter(b -> b.getAll().size() == 2
                        && b.getAll().stream().allMatch(q -> SpecialQuests.EMPTY_ROOM.equals(q.getWantedId())))
                .findFirst().orElse(null);
    }

    private boolean emptyRoomsBatchPosted() {
        return emptyRoomsBatch() != null;
    }

    private boolean emptyRoomsBatchComplete() {
        MCQuestBatch b = emptyRoomsBatch();
        return b != null && b.getAll().stream().allMatch(Quest::isComplete);
    }

    void addEmptyRoomsBatch(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        // Two NEW rooms: an empty-room quest only completes on a room no completed quest has
        // claimed (isClaimedByACompletedQuest), so existing rooms do not satisfy these.
        q.addNewQuest(roomQuest(batchUUID, SpecialQuests.EMPTY_ROOM));
        q.addNewQuest(roomQuest(batchUUID, SpecialQuests.EMPTY_ROOM));
        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_EMPTY_ROOMS));
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.info("Tutorial empty-rooms batch added (two new rooms before the cook batch)");
    }

    private @Nullable Tutorial tryPhase2_5_jobChangeAndFood(TutorialTownView view) {
        // Two villagers: the hunter and the one who becomes the cook. Villager 2 is the
        // hunter-gatherer batch's reward, villager 3 this batch's (2026-10-18).
        if (view.villagerCount() < 2) {
            return Tutorial.SKIPPED;
        }
        if (questBatches.getAll().stream().filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE).count() < 2) {
            if (!hunterRawFoodLootComplete()) {
                return Tutorial.SKIPPED;
            }
            addQuestsForJobChangeAndFood(view);
            // The batch carries the first room upgrade (Small Storeroom -> Storeroom): open
            // the upgrades lesson (entries/lesson_upgrades) with it.
            view.fireTutorialTrigger(TutorialTrigger.Triggers.FirstRoomUpgrade);
            notifyNewQuests(view);
            return Tutorial.APPLIED;
        }
        if (questBatches.getAll().stream().filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE && q.isComplete()).count() < 2) {
            return Tutorial.SKIPPED;
        }
        return null;
    }

    /**
     * The fixed post-cook miner beat (2026-10-14 playtest ruling,
     * `.scratch/playtest-2026-10-14/onboarding-miner-after-cook.md`): same pattern as
     * the hunter and cook beats - the room the miner needs + the pickaxes it holds +
     * N of its initial item (coal), and a JOB_CHANGE that the BOP-spend onboarding
     * guarantee (markOnboarding) routes to miner/coal. Posted after the two
     * onboarding job changes (hunter + cook) have landed, while no JOB_CHANGE is
     * open. Coal becomes the town's top need the moment the kitchen starts feeding,
     * so this fixed beat lands exactly where the coal pain starts; the need-driven
     * top-need batch below it picks up whatever need is NOT coal.
     */
    private @Nullable Tutorial tryPhase2_7_miner(TutorialTownView view) {
        if (jobChangesComplete() < 2) {
            // Two completed job changes (hunter + cook) = "after cook".
            return Tutorial.SKIPPED;
        }
        if (view.villagerCount() < 3) {
            return Tutorial.SKIPPED;
        }
        if (minerBeatPosted()) {
            // Posted once. Re-posting it on every batch request let backfill close each copy at
            // once and its reward bring another villager (2026-10-22: 8 arrivals in 3 s).
            return null;
        }
        if (questBatches.includes(q -> !q.isComplete() && q.getType() == Quest.QuestType.JOB_CHANGE)) {
            // A job change is in flight; wait for it to land (or be dropped) before
            // the miner beat's BOP spend has a clean guarantee slot.
            return null;
        }
        addMiner(view);
        notifyNewQuests(view);
        return Tutorial.APPLIED;
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

        MCQuest coalQuest = MCQuest.item(batchUUID, null, Compat.getItemId(Items.COAL), 8);
        coalQuest.setFlavorText("Stock the town on coal: place 8 coal in your store room chest so the furnaces keep running.");
        q.addNewQuest(coalQuest);

        MCQuest mineshaftQuest = MCQuest.standalone(batchUUID, null, view.getRoomForJob(new JobID("miner", "coal")));
        mineshaftQuest.setFlavorText("The miner needs a mineshaft to work in. Place a mineshaft block in a room and register the door.");
        q.addNewQuest(mineshaftQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_MINER));
        // Onboarding-flow quest: like the other tutorial-phase batches, it is
        // onboarding-flagged so the open miner JOB_CHANGE quest fills the BOP-spend
        // guarantee slot (onboardingJobChangeGuarantee) instead of a pre-roll.
        markOnboarding(qq);
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #2.5 (miner) was added to town: {}", qq.toNiceString());
    }

    /**
     * The emergent batch after the cook lands: names the town's most-requested
     * unmet supply (ranked from the economics need queue) and posts "build the
     * base room of the job that produces it". The content is need-driven, not
     * scripted: whichever root produces the top need wins (coal -> miner today),
     * and if nothing can produce the top supply the batch is skipped and the
     * fixed phases continue. One beat at a time (an open top-need room quest
     * locks it); a producer some townie already has, or whose room was already
     * built for this beat, is skipped.
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
        if (topNeedBeatOpen(questBatches.getAll())) {
            // Lock-until-complete: the in-flight top-need room quest stays pinned to its target.
            // Don't re-roll the top need or post a fresh quest until it completes. This removes
            // the per-menu / per-tick churn the playtest flagged.
            return Tutorial.SKIPPED;
        }
        ServerLevel level = t.getServerLevel();
        Set<String> unlocked = unlockedRoots(ti);
        Set<String> excluded = new HashSet<>(unlocked);
        excluded.addAll(builtTopNeedProducers(questBatches.getAll()));
        TopNeedChoice choice = chooseTopNeed(
                needs,
                key -> ServerJobsRegistry.staticProducerRootsForItem(level, Ingredients.fromString(key)),
                excluded
        );
        if (choice.need() == null) {
            // Nothing in the ranking has a producer the town could unlock (player input
            // or already-unlocked capacity); nothing to name. Continue the fixed phases.
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
        // Room only (dev, 2026-10-22): the old "Unlock job" quest asked for a BOP spend that rolls
        // a random root, so it rarely delivered the producer.
        MCQuest roomQuest = MCQuest.standalone(batchUUID, null, room);
        roomQuest.setTopNeedProducer(producer);
        roomQuest.setFlavorText("Your town keeps running out of " + itemName + ". A " + producer
                + " can make it. Build the " + producer + " room and register its door so one has a place to work.");
        q.addNewQuest(roomQuest);
        MCQuestBatch qb = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_NEW_JOB_ROOM));
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
    // Per load; the advancement grant itself is idempotent.
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

    /**
     * The onboarding cook beat's job: a REGISTERED cook job. It used to name the simple-furnace
     * template's source id (cook/simple_furnace_food), which ResourceJobLoader expands and
     * never registers - every job change through it hit ServerJobsRegistry's ERROR fallback
     * (2026-10-17 playtest). cook/extract is the cook progression root.
     */
    public static final JobID ONBOARDING_COOK_JOB = new JobID("cook", "extract");

    /** The post-cook miner beat's job (phase 2.7). */
    static final JobID ONBOARDING_MINER_JOB = new JobID("miner", "coal");

    void addHunterGatherer(TutorialTownView view) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);

        MCQuest rawFoodQuest = MCQuest.loot(
                batchUUID, null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), 1
        );
        rawFoodQuest.setFlavorText("Your villagers need food. A villager must bring back raw food.");
        q.addNewQuest(rawFoodQuest);

        // Day-2 playtest: consolidate the two sword asks (4 wooden + 1 stone) into a single
        // "4 stone swords" item quest - one sword quest only.
        MCQuest swordQuest = MCQuest.item(batchUUID, null, Compat.getItemId(Items.STONE_SWORD), 4);
        swordQuest.setFlavorText("Craft 4 stone swords and place them in your store room chest. Each hunter carries one.");
        q.addNewQuest(swordQuest);

        MCQuest hunterQuest = MCQuest.jobChange(batchUUID, null, new JobID("hunter", "sword"));
        hunterQuest.setFlavorText("Right-click a villager and use Blocks of Progress");
        q.addNewQuest(hunterQuest);

        // No flavor text: the card shows the standard room-recipe tooltip ("Build a room
        // containing the blocks shown here"), dev preference 2026-10-18.
        MCQuest bedroomQuest = MCQuest.standalone(batchUUID, null, SpecialQuests.BEDROOM);
        q.addNewQuest(bedroomQuest);

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_HUNTER_GATHERER));
        // Onboarding-flow quest: like the other tutorial-phase batches (see
        // addQuestsForJobChangeAndFood), it is onboarding-flagged. Without the flag the
        // open hunter JOB_CHANGE quest is invisible to onboardingJobChangeGuarantee, and
        // a BOP spend with no BOP blessing pre-rolls arbitrarily instead of promising
        // the hunter the quest names (2026-10-14 playtest: pre-roll fisher beat the
        // intended first hunter).
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

        MCQuest cookedFoodQuest = MCQuest.loot(
                batchUUID, null, TagsInit.Items.VILLAGER_FOOD.location(), 8
        );
        cookedFoodQuest.setFlavorText("Feed your growing village. Produce eight pieces of food from the raw food you have gathered.");
        q.addNewQuest(cookedFoodQuest);

        MCQuest kitchenQuest = MCQuest.standalone(batchUUID, null, KITCHEN_SMALL);
        kitchenQuest.setFlavorText("The cook needs a kitchen. Place a furnace in a room and register the door.");
        q.addNewQuest(kitchenQuest);

        // Storage starts to fill around the kitchen beat (playtests 2026-10-12, 2026-10-18), so
        // the Small Storeroom (1 chest) -> Storeroom (2 chests) upgrade rides this batch. It
        // replaces the old storage-upgrade phase 6 (dev, 2026-10-18).
        MCQuest storageUpgradeQuest = MCQuest.upgrade(batchUUID, null, SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM);
        storageUpgradeQuest.setFlavorText("Storage fills fast. Upgrade before production stops.");
        q.addNewQuest(storageUpgradeQuest);

        // This batch brings villager 3, so it carries a bed for them: the hunter-gatherer
        // bedroom (2 beds) becomes a big bedroom (4 beds). Onboarding rule (dev, 2026-10-18):
        // a batch that brings a villager carries the bed that villager will sleep in.
        // No flavor text: the standard room-upgrade tooltip.
        q.addNewQuest(MCQuest.upgrade(batchUUID, null, SpecialQuests.BEDROOM, BIG_BEDROOM));

        MCQuestBatch qq = q.withRewardUponCompletion(view.makeReward(TutorialTownView.PHASE_JOB_CHANGE_FOOD));
        // Onboarding-flow quest: like the other tutorial-phase batches, it is
        // onboarding-flagged (see `.scratch/cook-skill-unlock/cook-skill-unlock.md`).
        // The cook it staffs gets the starter recipe (cook/mutton) through the
        // root's default work — the simple-furnace template's `starter_recipes`
        // lists it, so every cook (onboarding or not) unlocks extract + fuel +
        // mutton at staff (see ChangeJobReward.getApplier / ServerJobsRegistry).
        // Its room quest completes on a superset room per the
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
        if (SpecialQuests.EMPTY_ROOM.equals(q) && isClaimedByACompletedQuest(room)) {
            return;
        }
        questBatches.markRecipeAsComplete(room, q);
    }

    /**
     * An "empty room" quest asks for a NEW room: it matches any registered room, so without this
     * every later empty-room quest would close at once on rooms the town already has (dev,
     * 2026-10-19: the post-hunter-gatherer batch is "two empty rooms").
     */
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

    /**
     * Chapter triggers at 5/10/20 completed procedural batches. The first batch posts no
     * broadcast of its own (2026-10-15 playtest ruling: the "village is thriving" line
     * carried no information the per-quest "Quest completed: …" lines didn't already —
     * see .scratch/day1-playtest-2026-10-11/village-is-thriving-message-confusing.md).
     */
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

    /**
     * Whether a villager arrival may land in a town that already has
     * {@code villagerCount} villagers. Playtest ruling (2026-10-15, restated 2026-10-17):
     * in an onboarding town the second villager arrives ONLY after the raw-food quest
     * has been completed. The first villager is never held, and a town that never ran
     * onboarding (test harnesses, hand-grown towns) is never held. "No raw-food quest
     * posted yet" is NOT satisfied - that reading let the gate's and the kickoff's
     * arrivals land on the same morning.
     */
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

    /**
     * The change-profession tab's red dot (2026-10-17 playtest): a JOB_CHANGE quest is open
     * and this villager is still on the gatherer root every arriving townie starts on
     * ({@code SpawnVisitorReward}), i.e. it is a candidate to spend the BOP on.
     */
    public boolean wantsJobChangeFrom(JobID villagerJob) {
        if (!GATHERER_ROOT.equals(villagerJob.rootId())) {
            return false;
        }
        return getAll().stream().anyMatch(
                q -> q.getType() == Quest.QuestType.JOB_CHANGE && !q.isComplete()
        );
    }

    private static final String GATHERER_ROOT = "gatherer";

    /** The campfire quest is posted and not yet complete: the flag keeps looking for a lit campfire. */
    public boolean isCampfireQuestOpen() {
        return getAll().stream().anyMatch(
                q -> SpecialQuests.CAMPFIRE.equals(q.getWantedId()) && !q.isComplete()
        );
    }

    /** The day-one campfire quest is posted and complete (a flag with its camp). */
    public boolean isCampfireQuestComplete() {
        return getAll().stream().anyMatch(
                q -> SpecialQuests.CAMPFIRE.equals(q.getWantedId()) && q.isComplete()
        );
    }

    /**
     * Splits one morning's queued rewards: arrivals pass {@link #isArrivalGateOpen} one at
     * a time, each released arrival counting toward the next check, so two queued spawns
     * on an empty town release one and hold the other. Non-arrival rewards always pass.
     */
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

    /**
     * Open-quest progress: wantedId -> how close the town is to the count. For
     * ITEM quests that is how many matching items the town containers already
     * hold (the "(n/target)" numerator on the quest card); for LOOT quests it is
     * the quest's own extraction total (the 2026-10-15 accumulation counter —
     * containers are irrelevant: extracted food may have been eaten or cooked
     * in the meantime). Same container scan and ingredient matching as the ITEM
     * completion path ({@code processItemQuests}), so the displayed ITEM count
     * is the one that completes the quest. Empty when no open ITEM/LOOT quest.
     */
    public Map<ResourceLocation, Integer> openQuestProgress() {
        List<MCQuest> open = questBatches.getAll().stream()
                .filter(q -> (q.getType() == Quest.QuestType.ITEM || q.getType() == Quest.QuestType.LOOT)
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
        Map<ResourceLocation, Integer> out = new HashMap<>();
        for (MCQuest q : open) {
            if (q.getType() == Quest.QuestType.LOOT) {
                out.put(q.getWantedId(), q.getExtracted());
            } else {
                out.put(q.getWantedId(), stacks.stream()
                        .filter(s -> questMatchesStack(q.getWantedId(), s))
                        .mapToInt(s -> s.toQTItemStack().getCount())
                        .sum());
            }
        }
        return out;
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
            Optional<MCRoom> eligible = rooms.stream()
                    .map(RoomRecipeMatch::getRoom)
                    .filter(r -> !SpecialQuests.EMPTY_ROOM.equals(q.getWantedId()) || !isClaimedByACompletedQuest(r))
                    .findFirst();
            if (eligible.isPresent()) {
                markQuestAsComplete(eligible.get(), q.getWantedId());
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

    /**
     * The onboarding job-change guarantee (ruling D, 2026-10-13, expected-guaranteed-cook-got-arborist.md):
     * while an onboarding JOB_CHANGE quest is open, its wanted root is what a BOP spend promises,
     * so it fills the same guarantee slot a blessed BOP's NBT uses. Without this, a spend with no
     * BOP blessing pre-rolls arbitrarily - the onboarding cook quest lost to an arborist in the
     * 2026-10-13 incident, and the first hunter lost to a fisher in the 2026-10-14 playtest
     * (the hunter-gatherer batch was unflagged until addHunterGatherer gained markOnboarding).
     * The oldest open onboarding JOB_CHANGE quest for this townie wins
     * (owner null, or this townie); empty once the onboarding job-change quests are done.
     */
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

    /**
     * Completes the active JOB_CHANGE quest for a townie the moment its BOP spend is
     * requested, not when the root change applies in the morning: the reward lands on the
     * player's act. Only a quest whose wanted root matches the decided pending root (blessed
     * guarantee or pre-roll) completes, and never one already owned by a different townie
     * (the quest-level owner: a batch can hold several JOB_CHANGE quests for the same town
     * with different owners). Returns whether a quest completed so the caller can log the
     * spend outcome.
     */
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
        // asIngredient resolves item ids to item ingredients and tag ids to tag
        // ingredients, so ITEM quests with a tag want now match their tag members.
        return Ingredients.asIngredient(wantedId).test(stack.toQTItemStack());
    }

    /**
     * LOOT quests accumulate on production: when a townie extracts loot matching an open
     * LOOT quest, the extracted count is credited to the quest and it completes once the
     * extraction total reaches its count (2026-10-15 ruling, reversing the 2026-10-04
     * close-on-first-extraction; count-1 quests keep that behaviour, 1 >= 1). Every
     * matching open LOOT quest is credited by one extraction (a re-issued ask after a
     * batch reward is not starved by the first match). The extracted item is
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
            QT.QUESTS_LOGGER.info("[LOOTTRACE] extract '{}' vs LOOT quest {} -> {} (extracted {}/{})",
                    stack.getItem() != null ? stack.getItem().getName(stack).getString() : "?",
                    quest.getWantedId(), match, quest.getExtracted() + stack.getCount(), quest.getCountNeeded());
            if (match) {
                // No early return: this extraction also advances any sibling
                // matching quest, and only completes a quest that reached its
                // count.
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
