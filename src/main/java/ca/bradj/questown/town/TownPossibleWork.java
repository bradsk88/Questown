package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.DeclarativeJob;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.requests.BoardRequests;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.DiningStages;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.needs.GateReason;
import ca.bradj.questown.town.needs.RequestsDone;
import ca.bradj.questown.town.needs.UnmetNeedTracker;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import ca.bradj.questown.world.MinecraftWorldAccess;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Predicate;
import com.google.common.base.Suppliers;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ca.bradj.questown.mc.Util.info;

/**
 * NOTE: This class is ONLY used for determining what jobs a villager CAN take on.
 * It has nothing to do with actual work logic or job execution.
 * For actual job execution, see {@link DeclarativeJob} and {@link DeclarativeJobTicker}.
 * For job status computation during execution, see {@link JobStatuses}.
 */
public class TownPossibleWork {

    /** The smelter fuel tag. An unlit furnace with something to smelt is reported as needing this, not as missing a room. */
    public static final String SMELTER_FUEL_KEY = "#" + ItemTags.COALS.location().toString();

    private final UnsafeTown town = new UnsafeTown(getClass());

    private final Map<String, List<JobID>> preselectedJobs = new HashMap<>();
    /** The warp's job list, scored without slot preconditions, since the warp fills the slots itself. */
    private final Map<String, List<JobID>> warpPreselectedJobs = new HashMap<>();
    private final Map<UUID, JobCycler> villagerCyclers = new HashMap<>();
    private final Queue<String> rootsAwaitingRecompute = new ArrayDeque<>();
    private @Nullable RootScoring scoringInProgress;
    private boolean shouldRecompute = true;
    private int buffer;

    /** The town's current unmet needs, by villager and item. */
    private final UnmetNeedTracker unmetNeeds = new UnmetNeedTracker();
    // Idle villagers whose requests are all stocked.
    private final Set<UUID> requestsDone = new HashSet<>();

    /** Half a millisecond of each tick for rescoring jobs. */
    private static final long RECOMPUTE_NANOS_PER_TICK = 500_000;

    public TownPossibleWork() {
    }

    public void initialize(TownFlagBlockEntity serverLevel) {
        town.initialize(serverLevel);
    }

    public void tick() {
        if (spendTickBudgetOnRecompute()) {
            return;
        }
        if (!shouldRecompute) {
            return;
        }
        buffer++;
        int freq = Config.WORK_PRECOMPUTE_FREQUENCY.get().intValue();
        buffer = buffer % freq;
        if (buffer != 0) {
            return;
        }
        beginRecomputePass();
        spendTickBudgetOnRecompute();
    }

    /**
     * Immediately recomputes possible jobs without rate limiting.
     * Used during time warp when tick() won't be called.
     */
    public void recomputeNow() {
        beginRecomputePass();
        while (scoreOneJob()) {
        }
    }

    /** Spreads a rescoring pass over as many ticks as it needs to stay within budget. */
    private boolean spendTickBudgetOnRecompute() {
        if (!isRecomputePassInProgress()) {
            return false;
        }
        long deadline = System.nanoTime() + RECOMPUTE_NANOS_PER_TICK;
        do {
            if (!scoreOneJob()) {
                return false;
            }
        } while (System.nanoTime() < deadline);
        return true;
    }

    private boolean isRecomputePassInProgress() {
        return scoringInProgress != null || !rootsAwaitingRecompute.isEmpty();
    }

    private void beginRecomputePass() {
        TownFlagBlockEntity t = town.getUnsafe();
        rootsAwaitingRecompute.clear();
        scoringInProgress = null;
        t.getVillagerHandle().getJobs().stream().map(JobID::rootId).forEach(rootsAwaitingRecompute::add);
        shouldRecompute = false;
    }

    private boolean scoreOneJob() {
        if (scoringInProgress == null) {
            String root = rootsAwaitingRecompute.poll();
            if (root == null) {
                return false;
            }
            scoringInProgress = new RootScoring(root, jobsOfRoot(root));
            return true;
        }
        TownFlagBlockEntity t = town.getUnsafe();
        Map.Entry<JobID, Supplier<Work>> job = scoringInProgress.remainingJobs.poll();
        if (job != null) {
            scoringInProgress.scored.add(evaluateJob(t, job));
            return true;
        }
        RootScoring finished = scoringInProgress;
        scoringInProgress = null;
        applyScores(t, finished.root, finished.scored);
        return true;
    }

    private static Queue<Map.Entry<JobID, Supplier<Work>>> jobsOfRoot(String root) {
        return Works.regularJobs()
                    .stream()
                    .filter(v -> root.equals(v.getKey().rootId()))
                    .collect(Collectors.toCollection(ArrayDeque::new));
    }

    private static final class RootScoring {
        private final String root;
        private final Queue<Map.Entry<JobID, Supplier<Work>>> remainingJobs;
        private final List<JobPossibility> scored = new ArrayList<>();

        private RootScoring(
                String root,
                Queue<Map.Entry<JobID, Supplier<Work>>> remainingJobs
        ) {
            this.root = root;
            this.remainingJobs = remainingJobs;
        }
    }

    private void applyScores(
            TownFlagBlockEntity t,
            String root,
            List<JobPossibility> unfilteredJobs
    ) {
        bigLog(t, root, unfilteredJobs);

        ImmutableList<JobID> preselected = band(unfilteredJobs, v -> v.score.value);
        preselectedJobs.put(root, preselected);
        warpPreselectedJobs.put(root, band(unfilteredJobs, JobPossibility::warpScore));
        // Needs come from the world, not the score: a job can score well and still wait on a supply.
        refreshUnmetNeeds(root, unfilteredJobs);
        t.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                "Prepared for {}: [{}]",
                root,
                Strings.join(preselected.stream().map(JobID::jobId).toList(), ",")
        );
    }

    private static ImmutableList<JobID> band(
            List<JobPossibility> unfilteredJobs,
            java.util.function.ToDoubleFunction<JobPossibility> score
    ) {
        return JobScoreBands.band(
                unfilteredJobs, score, Config.PREFERRED_JOB_ACCEPTANCE.get(), Config.MIN_JOB_ACCEPTANCE.get()
        ).stream().map(v -> v.jobID).collect(ImmutableList.toImmutableList());
    }

    private static void bigLog(
            TownFlagBlockEntity t,
            String root,
            List<JobPossibility> unfilteredJobs
    ) {
        if (!t.isDebugLogConsuming(DebugLogArgument.JOB_POSSIBILITIES_COMPUTE)) {
            return;
        }
        t.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                "Possible jobs for root {}: [\n{}\n]", root,
                Strings.join(
                        unfilteredJobs.stream()
                                      .map(JobPossibility::toString)
                                      .toList(),
                        "\n"
                )
        );
    }

    /** A root's unmet needs this pass: every key across its unlocked jobs, compared with what was tracked before. */
    private void refreshUnmetNeeds(String root, List<JobPossibility> unfilteredJobs) {
        try {
            TownFlagBlockEntity t = town.getUnsafe();
            ServerLevel sl = t.getServerLevel();
            long tick = Util.getTick(sl);
            Map<String, GateReason> liveKeys = new HashMap<>();
            Map<String, Set<JobID>> keyJobs = new HashMap<>();
            // Keys behind open requests come first in the bubble.
            Map<String, Set<JobID>> openKeyJobs = new HashMap<>();
            for (JobPossibility p : unfilteredJobs) {
                if (p.facts() == null || p.board() == null) {
                    continue;
                }
                GateReason reason = gateReason(t, p);
                // Only jobs a villager has unlocked count.
                if (!t.getVillagerHandle().isUnlocked(p.jobID())) {
                    continue;
                }
                String key = reason.itemKey();
                if (key == null) {
                    continue;
                }
                keyJobs.computeIfAbsent(key, k -> new HashSet<>()).add(p.jobID());
                if (p.board().answersOpen()) {
                    openKeyJobs.computeIfAbsent(key, k -> new HashSet<>()).add(p.jobID());
                }
                GateReason existing = liveKeys.get(key);
                // A supply item beats a product with no item.
                if (existing == null || (reason.supplyItem() && !existing.supplyItem())) {
                    liveKeys.put(key, reason);
                }
            }
            List<VisitorMobEntity> townies = t.getVillagerHandle().entities().stream()
                .map(v -> (VisitorMobEntity) v)
                .filter(v -> root.equals(v.getJobId().rootId()))
                .toList();
            List<UnmetNeedTracker.FirstEnter> firstEnters = unmetNeeds.applyPass(
                    tick, root, townies.stream().map(VisitorMobEntity::getUUID).toList(), liveKeys);
            NoMCEconomics econ = t.getEconomicsHandle();
            for (UnmetNeedTracker.FirstEnter fe : firstEnters) {
                if (!fe.reason().supplyItem()) {
                    continue;
                }
                // Recorded once, on the first enter; only a supply item goes in the item store.
                econ.registerUnmetNeed(tick, fe.townie(), fe.key());
            }
            for (VisitorMobEntity villager : townies) {
                boolean shownItemOrRoom = villager.getNeed() == TownieNeed.UNMET_ITEM
                        || villager.getNeed() == TownieNeed.MISSING_ROOM;
                if (WorkSeekerJob.isSeekingWork(villager.getJobId()) && hasDoneAllRequests(t, villager.getUUID(), unfilteredJobs)) {
                    requestsDone.add(villager.getUUID());
                    if (shownItemOrRoom) {
                        villager.setNeed(TownieNeed.NONE);
                    }
                    continue;
                }
                requestsDone.remove(villager.getUUID());
                if (!showsUnmetNeed(villager.getJobId(), villager.getStatusForServer())) {
                    if (shownItemOrRoom) {
                        villager.setNeed(TownieNeed.NONE);
                    }
                    continue;
                }
                // A need behind an open request outranks one from an unrequested job.
                Predicate<String> holdsOpen = key -> canHoldAnyOf(t, villager.getUUID(), openKeyJobs.get(key));
                String primary = unmetNeeds.primaryKey(root, villager.getUUID(), holdsOpen);
                String room = primary != null ? null : unmetNeeds.primaryRoom(root, villager.getUUID(), holdsOpen);
                if (primary == null && room == null) {
                    Predicate<String> holds = key -> canHoldAnyOf(t, villager.getUUID(), keyJobs.get(key));
                    primary = unmetNeeds.primaryKey(root, villager.getUUID(), holds);
                    room = primary != null ? null : unmetNeeds.primaryRoom(root, villager.getUUID(), holds);
                }
                if (room != null) {
                    if (villager.getNeed() != TownieNeed.MISSING_ROOM && villager.getNeed() != TownieNeed.NO_STORAGE) {
                        villager.setNeed(TownieNeed.MISSING_ROOM);
                    }
                    if (!room.equals(villager.getUnmetItem())) {
                        villager.setUnmetItem(room);
                    }
                    continue;
                }
                if (primary != null) {
                    // No storage outranks it.
                    if (villager.getNeed() != TownieNeed.UNMET_ITEM && villager.getNeed() != TownieNeed.NO_STORAGE) {
                        villager.setNeed(TownieNeed.UNMET_ITEM);
                    }
                    if (!primary.equals(villager.getUnmetItem())) {
                        villager.setUnmetItem(primary);
                    }
                } else if (shownItemOrRoom) {
                    villager.setNeed(TownieNeed.NONE);
                }
            }
        } catch (Exception e) {
            QT.FLAG_LOGGER.error("Failed to refresh unmet needs for root: {}", root, e);
        }
    }

    private static boolean hasDoneAllRequests(
            TownFlagBlockEntity t,
            UUID villager,
            List<JobPossibility> jobs
    ) {
        List<RequestsDone.JobView> views = jobs.stream()
                .filter(p -> p.facts() != null && p.board() != null)
                .filter(p -> t.getVillagerHandle().isUnlocked(villager, p.jobID()))
                .map(p -> new RequestsDone.JobView(
                        p.board().answersOpen(),
                        p.board().answersStocked(),
                        ServerJobsRegistry.makesNothing(p.jobID())
                ))
                .toList();
        return RequestsDone.allDone(views);
    }

    /** Whether this idle villager's requests are all stocked. Server only. */
    public boolean hasDoneAllRequests(UUID townie) {
        return requestsDone.contains(townie);
    }

    /** Whether this villager has unlocked any of the jobs that need a key, so it asks only for its own needs. */
    private static boolean canHoldAnyOf(TownFlagBlockEntity t, UUID townie, @Nullable Set<JobID> jobs) {
        if (jobs == null) {
            return false;
        }
        for (JobID j : jobs) {
            if (t.getVillagerHandle().isUnlocked(townie, j)) {
                return true;
            }
        }
        return false;
    }

    /** Why the best-scoring job can't start. An unlit smelter with input counts as a fuel need. */
    private static GateReason gateReason(TownFlagBlockEntity t, JobPossibility p) {
        JobFacts facts = p.facts();
        RequestCheck board = p.board();
        if (facts.blockedOnLabel() != null) {
            return new GateReason.Blocked(0, List.of(facts.blockedOnLabel()), List.of());
        }
        return GateReason.classify(
                facts.anyStateHasRoom(),
                facts.roomKey(),
                facts.firstBlockedState(),
                facts.missingIngredientLabel() == null ? List.of() : List.of(facts.missingIngredientLabel()),
                facts.missingToolLabel() == null ? List.of() : List.of(facts.missingToolLabel()),
                board.satisfied() != null,
                supplyKey(t, p.work())
        );
    }

    private static @Nullable String supplyKey(TownFlagBlockEntity t, Work work) {
        if (work.initialRequest == null) {
            return null;
        }
        try {
            Ingredient product = work.initialRequest.apply(t.getServerLevel());
            return product == null ? null : Ingredients.toString(product);
        } catch (RuntimeException e) {
            QT.FLAG_LOGGER.error("Failed to name supply item for job {}", work.id, e);
            return null;
        }
    }

    private record JobPossibility(
            JobID jobID,
            WithReason<Double> score,
            double warpScore,
            @Nullable JobFacts facts,
            @Nullable RequestCheck board,
            Work work
    ) {
        @Override
        public String toString() {
            return "JobPossibility{" +
                    "jobID=" + jobID +
                    ", score=" + score.map(v -> NumberFormat.getNumberInstance()
                                                            .format(Math.round(v * 1000.0) / 1000.0)) +
                    '}';
        }
    }

    private static JobPossibility evaluateJob(
            TownFlagBlockEntity t,
            Map.Entry<JobID, Supplier<Work>> w
    ) {
        Work work = w.getValue().get();
        Job<?, ?, ?> j = work.jobFunc.apply(UUID.randomUUID());
        if (!(j instanceof DeclarativeJob dj)) {
            return new JobPossibility(
                    w.getKey(),
                    WithReason.always(0.0, "Unsupported job class " + j.getClass().getName()),
                    0.0,
                    null,
                    null,
                    work
            );
        }

        dj.initialize(t.getServerLevel(), dj.getJournalSnapshot());

        // Check if product is requested
        RequestCheck board = boardCheck(t, j.getId());

        // Only a job with a slot precondition has a slot-blind view.
        boolean slotGated = work.getSlotPrecondition() != null;
        JobFacts facts = gateFacts(t, dj, false, slotGated);
        double v = dj.specialGlobalRules.contains(SpecialRules.ALWAYS_CONSIDER)
                ? JobScoreBands.alwaysConsiderScore(Config.PREFERRED_JOB_ACCEPTANCE.get(), Config.MIN_JOB_ACCEPTANCE.get())
                : (double) facts.highestState() / dj.getMaxState();
        float shuffler = Compat.nextRandomInt(t.getServerLevel(), 100) / 10000f;
        double warpV = facts.slotUnmet() ? (double) gateFacts(t, dj, true, true).highestState() / dj.getMaxState() : v;
        return new JobPossibility(
                w.getKey(),
                WithReason.always(
                        v + shuffler,
                        "Highest possible job state: " + facts.highestState() + " because " + facts.description()
                        + " (out of " + dj.getMaxState() + ", with randomizer " + shuffler + ")" + board.description()
                ),
                warpV + shuffler,
                facts,
                board,
                work
        );
    }

    private record JobFacts(
            int highestState,
            String description,
            boolean anyStateHasRoom,
            @Nullable String roomKey,
            int firstBlockedState,
            @Nullable String missingIngredientLabel,
            @Nullable String missingToolLabel,
            @Nullable String blockedOnLabel,
            boolean slotUnmet
    ) {
        JobFacts(
                int highestState,
                String description,
                boolean anyStateHasRoom,
                @Nullable String roomKey,
                int firstBlockedState,
                @Nullable String missingIngredientLabel,
                @Nullable String missingToolLabel,
                @Nullable String blockedOnLabel
        ) {
            this(highestState, description, anyStateHasRoom, roomKey, firstBlockedState, missingIngredientLabel, missingToolLabel, blockedOnLabel, false);
        }

        /** The job's site exists but no block is ready yet. Nothing is missing, so no need is recorded. */
        static JobFacts slotNotReady(String reason) {
            return new JobFacts(0, "Job site present but its slot precondition does not hold now (" + reason + ")",
                    true, null, -1, null, null, null, true);
        }

        JobFacts(
                int highestState,
                String description,
                boolean anyStateHasRoom,
                @Nullable String roomKey,
                int firstBlockedState,
                @Nullable String missingIngredientLabel,
                @Nullable String missingToolLabel
        ) {
            this(highestState, description, anyStateHasRoom, roomKey, firstBlockedState, missingIngredientLabel, missingToolLabel, null);
        }
    }

    /**
     * @param satisfied      the request the job may work for
     * @param answersOpen    the job answers an unpaused request
     * @param answersStocked the job answers only paused requests
     */
    private record RequestCheck(
            boolean boardEmpty,
            @Nullable WorkRequest satisfied,
            boolean answersOpen,
            boolean answersStocked
    ) {
        String description() {
            if (boardEmpty) {
                return " [No items requested on job board]";
            }
            if (satisfied != null) {
                return " [Satisfies request: " + satisfied + "]";
            }
            return " [Product not requested]";
        }
    }

    private static RequestCheck boardCheck(TownFlagBlockEntity t, JobID jobId) {
        BoardRequests requests = t.getWorkHandle().getBoardRequests();
        if (requests.isEmpty()) {
            return new RequestCheck(true, null, false, false);
        }
        WorksBehaviour.TownData td = t.getTownData();
        WorkRequest satisfied = ServerJobsRegistry.firstAnswerable(td, jobId, requests).orElse(null);
        boolean open = satisfied != null && ServerJobsRegistry.answersUnpaused(td, jobId, requests);
        boolean stocked = !open && ServerJobsRegistry.answersPaused(td, jobId, requests);
        return new RequestCheck(false, satisfied, open, stocked);
    }

    /**
     * @param ignoreSlot check job blocks without their slot precondition, as the warp does
     * @param slotGated  the job declares {@code block.has_item_in_slot_initially}
     */
    private static JobFacts gateFacts(TownFlagBlockEntity t, DeclarativeJob dj, boolean ignoreSlot, boolean slotGated) {
        if (dj.specialGlobalRules.contains(SpecialRules.ALWAYS_CONSIDER)) {
            return new JobFacts(dj.getMaxState(), "Special rule ALWAYS_CONSIDER present (scored between the acceptance bands)", true, null, -1, null, null);
        }
        WithReason<Boolean> lastRoomCheck = hasJobSite(t, dj, ignoreSlot);
        // A furnace with input and no fuel is a fuel need.
        if (!lastRoomCheck.value() && !ignoreSlot && slotGated && smelterFuelBlocker(t, dj) == null
                && hasJobSite(t, dj, true).value()) {
            return JobFacts.slotNotReady(lastRoomCheck.reason());
        }
        boolean townHasJobSite = lastRoomCheck.value();
        if (!townHasJobSite) {
            String roomReason = lastRoomCheck != null ? lastRoomCheck.reason() : "No states checked";
            String fuelBlocker = smelterFuelBlocker(t, dj);
            String fuelNote = fuelBlocker != null
                    ? " - a smelter is present but unlit; the missing input is fuel (" + fuelBlocker + ")"
                    : "";
            return new JobFacts(
                    0,
                    "Town lacks required job site (or descendant) of: " + dj.location().baseRoom() + " (" + roomReason + ")" + fuelNote,
                    false,
                    dj.location().baseRoom().toString(),
                    -1,
                    null,
                    null,
                    fuelBlocker
            );
        }
        ServerLevel sl = Preconditions.checkNotNull(t.getServerLevel());
        // The same for every state, so scan once per job.
        @Nullable List<ContainerTarget<MCContainer, MCTownItem>> jobSiteContainers = null;
        for (int i = 0; i < dj.getMaxState(); i++) {
            int ii = i;
            boolean townHasIngredient = true;
            final IPredicateCollection<MCHeldItem> ing = dj.getChecks().getIngredientsForStep(ii);
            if (ing != null && !ing.isEmpty()) {
                townHasIngredient = false;
                if (jobSiteContainers == null) {
                    Supplier<? extends Collection<Item>> stored = unique(t);
                    jobSiteContainers = Containers.get(
                            t,
                            r -> true,
                            bp -> isJobBlock(dj, bp, sl, ignoreSlot, stored),
                            js -> dj.location().baseRoom().equals(js),
                            false
                    );
                }
                for (ContainerTarget<MCContainer, MCTownItem> tg : jobSiteContainers) {
                    if (tg.hasItem(zzz -> ing.test(MCHeldItem.fromTown(zzz)))) {
                        townHasIngredient = true;
                        break;
                    }
                }
            }

            boolean townHasTool = true;
            final IPredicateCollection<MCTownItem> tool = dj.getChecks().getToolsForStep(ii);
            if (tool != null && !tool.isEmpty()) {
                townHasTool = townHasTool(t, tool);
            }

            if (!townHasIngredient || !townHasTool) {
                int max = Math.max(0, i - 1);
                return new JobFacts(
                        max,
                        "Lacking " +
                                (townHasIngredient ? "" : "ingredients ") +
                                (townHasTool ? "" : "tools ") +
                                "for job state " + ii + "/" + dj.getMaxState(),
                        true,
                        null,
                        ii,
                        !townHasIngredient ? label(dj.initialIngredients.get(ii)) : null,
                        !townHasTool ? label(dj.initialTools.get(ii)) : null
                );
            }
        }
        return new JobFacts(
                dj.getMaxState(),
                "All ingredients and tools available for all job states",
                true,
                null,
                -1,
                null,
                null
        );
    }

    private static @Nullable String label(@Nullable net.minecraft.world.item.crafting.Ingredient ingredient) {
        return ingredient == null ? null : Ingredients.toString(ingredient);
    }

    /** A town has a tool if any container or any villager holds it. */
    private static boolean townHasTool(
            TownFlagBlockEntity t,
            IPredicateCollection<MCTownItem> tool
    ) {
        if (t.findMatchingContainer(tool::test) != null) {
            return true;
        }
        return t.getVillagerHandle().entities().stream()
                .filter(v -> v instanceof VisitorMobEntity)
                .map(v -> (VisitorMobEntity) v)
                .flatMap(v -> {
                    ImmutableList.Builder<ItemStack> b = ImmutableList.builder();
                    for (int slot = 0; slot < v.getInventory().getContainerSize(); slot++) {
                        b.add(v.getInventory().getItem(slot));
                    }
                    return b.build().stream();
                })
                .anyMatch(stack -> !stack.isEmpty() && tool.test(MCTownItem.fromMCItemStack(stack)));
    }

    /** Whether any state of the job has a job block in a room. Otherwise, the last state's reason. */
    private static WithReason<Boolean> hasJobSite(TownFlagBlockEntity t, DeclarativeJob dj, boolean ignoreSlot) {
        WithReason<Boolean> last = WithReason.always(false, "No states checked");
        for (int i = 0; i < dj.getMaxState(); i++) {
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(i);
            if (UtilClean.getOrDefaultCollection(dj.specialRules, s, ImmutableList.of())
                         .contains(SpecialRules.CLAIM_SPOT)) {
                continue;
            }
            last = ignoreSlot ? dj.hasRoomsAtStateIgnoringSlot(t, i) : dj.hasRoomsAtState(t, i);
            if (last.value()) {
                return last;
            }
        }
        return last;
    }

    private static boolean isJobBlock(
            DeclarativeJob dj,
            BlockPos bp,
            ServerLevel sl,
            boolean ignoreSlot,
            Supplier<? extends Collection<Item>> stored
    ) {
        return dj.location().isJobBlock()
                 .test(new JobBlockTestContext(new MinecraftWorldAccess(sl), info(sl), bp, ImmutableList::of, stored, ignoreSlot, false));
    }

    /** A smelter job's room check failed but an unlit smelter with input is there: the town needs fuel. */
    private static @Nullable String smelterFuelBlocker(
            TownFlagBlockEntity t,
            DeclarativeJob dj
    ) {
        if (!dj.specialGlobalRules.contains(SpecialRules.FURNACE_SMELT_WARP)) {
            return null;
        }
        ServerLevel sl = t.getServerLevel();
        if (sl == null) {
            return null;
        }
        for (RoomRecipeMatch<ca.bradj.roomrecipes.serialization.MCRoom> room :
                 t.getRoomHandle().getRoomsMatching(dj.location().baseRoom())) {
            for (BlockPos bp : room.getContainedBlocks().keySet()) {
                var state = sl.getBlockState(bp);
                if (!(state.getBlock() instanceof AbstractFurnaceBlock) || state.getValue(AbstractFurnaceBlock.LIT)) {
                    continue;
                }
                // An empty unlit furnace is idle, not short of fuel.
                if (sl.getBlockEntity(bp) instanceof AbstractFurnaceBlockEntity f && !f.getItem(0).isEmpty()) {
                    return SMELTER_FUEL_KEY;
                }
            }
        }
        return null;
    }

    /** Read once per scan: it walks every town container. */
    private static Supplier<? extends Collection<Item>> unique(TownFlagBlockEntity t) {
        return Suppliers.memoize(() -> TownContainers.getUniqueItems(t));
    }

    public ImmutableList<JobID> getFor(JobID jobId) {
        return UtilClean.getOrDefaultCollection(preselectedJobs, jobId.rootId(), ImmutableList.of());
    }

    public ImmutableList<JobID> getForWarp(JobID jobId) {
        return UtilClean.getOrDefaultCollection(warpPreselectedJobs, jobId.rootId(), ImmutableList.of());
    }

    /** Dev-only: each of the root's jobs with its score and why it can't start, plus the board's requests. */
    public String scoreDump(String root) {
        TownFlagBlockEntity t = town.getUnsafe();
        StringBuilder b = new StringBuilder();
        b.append("preselected=").append(getFor(new JobID(root, "x"))).append("\n");
        b.append("warpPreselected=").append(getForWarp(new JobID(root, "x"))).append("\n");
        t.getWorkHandle().getRequestedResults().forEach(r -> b.append("board request: ").append(r).append("\n"));
        for (Map.Entry<JobID, Supplier<Work>> job : Works.regularJobs().stream().filter(v -> root.equals(v.getKey().rootId())).toList()) {
            JobPossibility p = evaluateJob(t, job);
            WithReason<Double> sc = p.score;
            b.append(job.getKey()).append(" score=").append(sc.map(v -> Math.round(v * 1000.0) / 1000.0))
             .append(" | ").append(sc.reason()).append("\n");
        }
        return b.toString();
    }

    public @Nullable JobID nextForVillager(
            UUID villagerUUID,
            JobID currentJob,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            ImmutableList<WorkRequest> requestedResults,
            WorksBehaviour.TownData td
    ) {
        JobCycler cycler = villagerCyclers.computeIfAbsent(villagerUUID, k -> new JobCycler());
        // Only the jobs this villager has unlocked.
        VillagerHolder vh = town.getUnsafe().getVillagerHandle();
        ImmutableList<JobID> possible = getFor(currentJob).stream()
                .filter(jobID -> vh.isUnlocked(villagerUUID, jobID))
                .collect(ImmutableList.toImmutableList());
        return cycler.nextValid(possible, canAlwaysStart, canFitInDay, requestedResults, td,
                job -> ServerJobsRegistry.tripLength(villagerUUID, job),
                ServerJobsRegistry::getAvailableAtLevel);
    }

    /** The supply this villager is recorded as blocked on, even when its bubble is hidden. */
    public @Nullable String trackedUnmetKey(String root, UUID townie) {
        return unmetNeeds.primaryKey(root, townie);
    }

    /** Only a struggling villager shows an unmet need: one looking for work or stuck for supplies or a site. Not one that is working, eating or relaxing. */
    public static boolean showsUnmetNeed(JobID currentJob, @Nullable IStatus<?> status) {
        if (WorkSeekerJob.isSeekingWork(currentJob)) {
            return true;
        }
        if (DowntimeWork.matches(currentJob) || DiningStages.isDiner(currentJob)) {
            return false;
        }
        return ProductionStatus.NO_SUPPLIES.equals(status) || ProductionStatus.NO_JOBSITE.equals(status);
    }

    public void invalidate() {
        shouldRecompute = true;
    }
}
