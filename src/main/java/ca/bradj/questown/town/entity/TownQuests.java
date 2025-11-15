package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.RoomBlock;
import ca.bradj.questown.blocks.entity.BlockAsRoomEntity;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.ItemEconomicsData;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedNoToolWorkQtrDay;
import ca.bradj.questown.logic.RoomRecipes;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.CoreProgression;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.*;
import ca.bradj.questown.town.rewards.*;
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
                  .map(v -> Arrays.stream(v.getItems()).map(ItemStack::getItem).map(ForgeRegistries.ITEMS::getKey)
                                  .filter(Objects::nonNull).map(ResourceLocation::toString).toList()).toList();
    }

    public static void addBatchForVisitor(
            TownInterface town,
            TownQuests quests,
            @Nullable VillagerUUID visitorUUID
    ) {
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
        ServerLevel level = town.getServerLevel();
        int size = getVillagers(this).size();

        if (!questRequests.isEmpty()) {
            Tutorial r = addTutorialBatches();
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
            pendingQuests = new QuestBatchSeed(level, UUID.randomUUID(), targetItemWeight);
        }

        QuestBatchSeed pop = pendingQuests;
        pendingQuests = null;

        boolean canGrowMore = pop.grow(
                town::hasEnoughBeds,
                () -> getNeededRooms(town.getEconomicsHandle()).stream().filter(v -> TownQuests.isNotSpecial(v.id()))
                                                               .toList(),
                () -> {
                    List<RoomRecipe> rs = level.getRecipeManager()
                                               .getAllRecipesFor(RecipesInit.ROOM).stream()
                                               .filter(v -> TownQuests.isNotSpecial(v.getId()))
                                               .toList();
                    List<RoomRecipe> recipes = new ArrayList<>(rs);
                    for (Supplier<RoomBlock> roomBlockSupplier : BlockAsRoomEntity.ALL) {
                        recipes.add(roomBlockSupplier.get().asRecipe());
                    }
                    List<ResourceLocation> ids = recipes.stream().map(RoomRecipe::getId).toList();
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

    private enum Tutorial {
        SKIPPED,
        APPLIED,
        DONE
    }

    private Tutorial addTutorialBatches() {
        if (questBatches.isEmpty()) {
            return Tutorial.SKIPPED;
        }

        TownFlagBlockEntity t = town.getUnsafe();
        if (questBatches.hasOneQuestOnly(SpecialQuests.CAMPFIRE::equals)) {
            // Phase one: Ask for the bare essentials (bedroom, storeroom, job board, gate)
            addQuestsForVillagerKickoff(t);
            return Tutorial.APPLIED;
        }

        if (!allVillagerKickoffQuestsDone()) {
            return Tutorial.SKIPPED;
        }

        if (town.getUnsafe().getVillagerHandle().entities().size() < 2) {
            return Tutorial.SKIPPED;
        }

        if (!questBatches.includes(q -> q.getType() == Quest.QuestType.JOB_CHANGE)) {
            // Phase two: Ask the player to complete at least one job change
            addQuestsForJobChangeAndFood(t);
            return Tutorial.APPLIED;
        }

        if (!questBatches.includes(q -> q.getType() == Quest.QuestType.JOB_CHANGE && q.isComplete())) {
            return Tutorial.SKIPPED;
        }

        @Nullable JobHaver jobToCreateRoomFor = getJobToCreateRoomFor();
        if (jobToCreateRoomFor != null) {
            // Phase three: Ask the player to provide the room for the new job
            addQuestForNewJobRoom(t, jobToCreateRoomFor);
            return Tutorial.APPLIED;
        }

        return Tutorial.DONE;
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

    private @Nullable JobHaver getJobToCreateRoomFor() {
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
                    QT.QUESTS_LOGGER.error("Job Change quest had no owner. This is likely a bug.");
                }
                jobToUseIfNoQuestsExist = new JobHaver(
                        batch.getOwner(),
                        JobID.fromRL(q.getWantedId())
                );
                if (jobRoomQuestExists(q, batch.getOwner())) {
                    return null;
                }
            }
        }
        return jobToUseIfNoQuestsExist;
    }

    private boolean jobRoomQuestExists(
            MCQuest jobChange,
            @NotNull VillagerUUID owner
    ) {
        JobID jobId = JobID.fromRL(jobChange.getWantedId());
        ResourceLocation room = ServerJobsRegistry.getRoomForJobId(jobId);
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

    private static final ImmutableList<ResourceLocation> VILLAGER_KICKOFF_QUESTS = ImmutableList.of(
            SpecialQuests.TOWN_GATE,
            SpecialQuests.BEDROOM,
            SpecialQuests.JOB_BOARD,
            SpecialQuests.STORE_ROOM_SMALL
    );

    private void addQuestsForVillagerKickoff(TownInterface town) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        VILLAGER_KICKOFF_QUESTS.forEach(k -> q.addNewQuest(roomQuest(batchUUID, k)));

        MCQuestBatch qb = q.withRewardUponCompletion(new MCRewardList(
                town,
                new MCDelayedReward(town, new SpawnVisitorReward(town, VillagerUUID.random())),
                new MCInstantReward(town, new AddBatchOfQuestsForVisitorReward(town, null))
        ));
        questBatches.add(qb);
        QT.QUESTS_LOGGER.debug("Tutorial quests added to town: {}", qb.toNiceString());
    }

    private MCQuest roomQuest(
            UUID batchUUID,
            ResourceLocation roomId
    ) {
        return MCQuest.standalone(batchUUID, null, roomId);
    }

    private void addQuestsForJobChangeAndFood(
            TownFlagBlockEntity town
    ) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        WorksBehaviour.TownData data = town.getTownData();
        CoreProgression<JobID, ResourceLocation> prog = new CoreProgression<>(
                town.getEconomicsHandle().getAggregatedItems(null),
                GathererUnmappedNoToolWorkQtrDay.ID::equals,
                jobs -> Compat.shuffle(jobs.iterator(), town.getServerLevel()).get(0),
                j -> producesFood(town, j),
                (j, needs) -> resourceImpact(needs, data, j)
        );
        JobID jc = getJobForFirstChangeQuest(prog);
        q.addNewQuest(MCQuest.item(batchUUID, null, Compat.getItemId(Items.APPLE), 10));
        q.addNewQuest(MCQuest.jobChange(batchUUID, null, jc));

        MCQuestBatch qq = q.withRewardUponCompletion(new AddBatchOfQuestsForVisitorReward(town, null));
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #2 was added to town: {}", qq.toNiceString());
    }

    private void addQuestForNewJobRoom(
            TownFlagBlockEntity t,
            JobHaver job
    ) {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs q = new MCQuestBatch.Inputs(batchUUID, null);
        ResourceLocation room = ServerJobsRegistry.getRoomForJobId(job.job());
        q.addNewQuest(MCQuest.standalone(batchUUID, job.villager(), room));

        // TODO: Add a quest for supplying the new worker with whatever they need
//        ImmutableList<Ingredient> wanted = ServerJobsRegistry.getWantedResourcesProvider(job.job())
//                                                             .apply(ImmutableList.of());
//        for (Ingredient ingredient : wanted) {
//            q.addNewQuest(MCQuest.item(batchUUID, job.villager(), Ingredients.toRL(), 1));
//        }

        MCQuestBatch qq = q.withRewardUponCompletion(new MCDelayedReward(
                town.getUnsafe(), defaultQuestCompletionRewards(town.getUnsafe())
        ));
        questBatches.add(qq);
        QT.QUESTS_LOGGER.debug("Tutorial batch #3 was added to town: {}", qq.toNiceString());
    }

    private @Nullable JobID getJobForFirstChangeQuest(CoreProgression<JobID, ResourceLocation> prog) {
        return prog.getFirstJobChange(
                ServerJobsRegistry.getAllRootJobs(),
                ServerJobsRegistry::getRoomForJobId,
                q -> questBatches.includes(v -> q.equals(v.getWantedId()))
        );
    }

    private Collection<ItemEconomicsData> resourceImpact(
            ImmutableList<ItemEconomicsData> needs,
            WorksBehaviour.TownData data,
            JobID j
    ) {
        ImmutableSet<MCTownItem> results = ServerJobsRegistry.getResults(data, j);
        return needs.stream().filter(v -> results.stream().anyMatch(z -> sameItem(v, z))).toList();
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
        town.getUnsafe().setChanged();
        String completionMessage = quest.getCompletionMessage();
        if (completionMessage == null || completionMessage.isBlank()) {
            return;
        }
        town.getUnsafe().messages.broadcastMessage(completionMessage);
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

    public void processItemQuests(ImmutableList<MCTownItem> allStacks) {
        questBatches.processItemQuests(allStacks, this::questMatchesStack, TownQuests.TRACKER);
    }

    public void processJobChanges(ImmutableMap<VillagerUUID, JobID> villagerJobs) {
        // TODO: Consider putting on QuestBatches and testing
        for (MCQuestBatch batch : questBatches.getAllBatches()) {
            for (MCQuest q : batch.getAll()) {
                if (q.getType() != Quest.QuestType.JOB_CHANGE) {
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

    private boolean questMatchesStack(
            ResourceLocation wantedId,
            MCTownItem stack
    ) {
        return wantedId.equals(Compat.getItemId(stack.get()));
    }
}
