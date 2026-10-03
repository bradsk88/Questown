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
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.econ.NoMCEconomics;
import ca.bradj.questown.town.needs.GateReason;
import ca.bradj.questown.town.needs.UnmetNeedTracker;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import ca.bradj.questown.world.MinecraftWorldAccess;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.*;
import java.util.function.Predicate;
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

    private final UnsafeTown town = new UnsafeTown(getClass());

    private final Map<String, List<JobID>> preselectedJobs = new HashMap<>();
    private final Map<UUID, JobCycler> villagerCyclers = new HashMap<>();
    private final Queue<String> rootsAwaitingRecompute = new ArrayDeque<>();
    private @Nullable RootScoring scoringInProgress;
    private boolean shouldRecompute = true;
    private int buffer;

    /**
     * The town's live unmet needs (design: .scratch/gannet-top-need/unmet-need-design.md):
     * idempotent per (townie, item key), since-at-first-enter, bounded by the roster.
     */
    private final UnmetNeedTracker unmetNeeds = new UnmetNeedTracker();

    /** Half a millisecond: a tenth of what a hitch needs to be visible, out of a 50ms server tick. */
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
            // Warp has no ticks to spread the work across, so the whole pass runs here.
        }
    }

    /**
     * Scoring one job scans the town's containers for each of its states, and a root can own
     * dozens of jobs (there is one cook job per cookable item). A whole pass in one tick is a
     * visible hitch, so a pass is spread over as many ticks as it takes to stay inside the budget.
     */
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

    /** @return false once the pass has nothing left to do. */
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
        // FIXME: Only include jobs that are known by the villagers
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

        List<JobPossibility> jobs = unfilteredJobs.stream().filter(
                v -> v.score.value > Config.PREFERRED_JOB_ACCEPTANCE.get()
        ).toList();
        if (jobs.isEmpty()) {
            jobs = unfilteredJobs.stream().filter(
                    v -> v.score.value > Config.MIN_JOB_ACCEPTANCE.get()
            ).toList();
        }
        ImmutableList<JobID> preselected = jobs.stream().map(v -> v.jobID).collect(ImmutableList.toImmutableList());
        preselectedJobs.put(root, preselected);
        if (jobs.isEmpty()) {
            registerUnmetNeeds(root, unfilteredJobs);
        } else {
            clearUnmetItemNeeds(root);
        }
        t.getDebugLogger(QT.FLAG_LOGGER, DebugLogArgument.JOB_POSSIBILITIES_COMPUTE).log(
                "Prepared for {}: [{}]",
                root,
                Strings.join(preselected.stream().map(JobID::jobId).toList(), ",")
        );
    }

    private static void bigLog(
            TownFlagBlockEntity t,
            String root,
            List<JobPossibility> unfilteredJobs
    ) {
        // Joining every job's toString is not cheap; skip it when the output would be discarded.
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

    private void registerUnmetNeeds(String root, List<JobPossibility> unfilteredJobs) {
        try {
            TownFlagBlockEntity t = town.getUnsafe();
            ServerLevel sl = t.getServerLevel();
            long tick = Util.getTick(sl);
            JobPossibility best = unfilteredJobs.stream()
                    .filter(p -> p.facts() != null && p.board() != null)
                    .max(
                            Comparator
                                    .comparingDouble((JobPossibility p) -> -p.score.value)
                                    .thenComparing(p -> p.jobID().jobId())
                    )
                    .orElse(null);
            if (best == null) {
                return;
            }
            GateReason reason = gateReason(t, best);
            NoMCEconomics econ = t.getEconomicsHandle();
            t.getVillagerHandle().entities().stream()
              .map(v -> (VisitorMobEntity) v)
              .filter(v -> root.equals(v.getJobId().rootId()))
              .forEach(villager -> {
                  String key = reason.itemKey();
                  // First enter of this (townie, key) only: one econ record and one bubble,
                  // not one per recompute pass (the run-2 swords=61 failure mode).
                  if (unmetNeeds.enter(tick, root, villager.getUUID(), key, reason) && key != null) {
                      villager.setNeed(TownieNeed.UNMET_ITEM);
                      villager.setUnmetItem(key);
                      econ.registerUnmetNeed(tick, villager.getUUID(), key);
                  }
              });
        } catch (Exception e) {
            QT.FLAG_LOGGER.error("Failed to register unmet needs for root: {}", root, e);
        }
    }

    /**
     * The best-scoring job's typed gate reason. Best-scoring (not random — the old
     * path picked a random work's first ingredient and mislabelled non-sword
     * failures as "swords"): the town's own statement of what it waits for.
     */
    private static GateReason gateReason(TownFlagBlockEntity t, JobPossibility p) {
        JobFacts facts = p.facts();
        RequestCheck board = p.board();
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

    /** The job's product supply key, or null when the job names no single product. */
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

    /** A townie with viable work again is no longer waiting on supplies. */
    private void clearUnmetItemNeeds(String root) {
        try {
            unmetNeeds.exitRoot(root);
            town.getUnsafe().getVillagerHandle().entities().stream()
                .map(v -> (VisitorMobEntity) v)
                .filter(v -> root.equals(v.getJobId().rootId()))
                .filter(v -> v.getNeed() == TownieNeed.UNMET_ITEM)
                .forEach(v -> v.setNeed(TownieNeed.NONE));
        } catch (Exception e) {
            QT.FLAG_LOGGER.error("Failed to clear unmet-item needs for root: {}", root, e);
        }
    }

    private record JobPossibility(
            JobID jobID,
            WithReason<Double> score,
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
                    null,
                    null,
                    work
            );
        }

        dj.initialize(t.getServerLevel(), dj.getJournalSnapshot());

        // Check if product is requested
        RequestCheck board = boardCheck(t, j.getId());

        JobFacts facts = gateFacts(t, dj);
        double v = (double) facts.highestState() / dj.getMaxState();
        float shuffler = Compat.nextRandomInt(t.getServerLevel(), 100) / 10000f;
        return new JobPossibility(
                w.getKey(),
                WithReason.always(
                        v + shuffler,
                        "Highest possible job state: " + facts.highestState() + " because " + facts.description()
                        + " (out of " + dj.getMaxState() + ", with randomizer " + shuffler + ")" + board.description()
                ),
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
            @Nullable String missingToolLabel
    ) {
    }

    private record RequestCheck(boolean boardEmpty, @Nullable WorkRequest satisfied) {
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
        ImmutableList<WorkRequest> requests = t.getWorkHandle().getRequestedResults();
        if (requests.isEmpty()) {
            return new RequestCheck(true, null);
        }
        WorksBehaviour.TownData td = t.getTownData();
        for (WorkRequest r : requests) {
            if (ServerJobsRegistry.canSatisfy(td, jobId, r.asIngredient())) {
                return new RequestCheck(false, r);
            }
        }
        return new RequestCheck(false, null);
    }

    private static JobFacts gateFacts(TownFlagBlockEntity t, DeclarativeJob dj) {
        if (dj.specialGlobalRules.contains(SpecialRules.ALWAYS_CONSIDER)) {
            return new JobFacts(dj.getMaxState(), "Special rule ALWAYS_CONSIDER present", true, null, -1, null, null);
        }
        WithReason<Boolean> lastRoomCheck = null;
        boolean townHasJobSite = false;
        for (int i = 0; i < dj.getMaxState(); i++) {
            ProductionStatus s = ProductionStatus.fromJobBlockStatus(i);
            if (!UtilClean.getOrDefaultCollection(dj.specialRules, s, ImmutableList.of())
                          .contains(SpecialRules.CLAIM_SPOT)) {
                lastRoomCheck = dj.hasRoomsAtState(t, i);
                if (lastRoomCheck.value()) {
                    townHasJobSite = true;
                    break;
                }
            }
        }
        if (!townHasJobSite) {
            String roomReason = lastRoomCheck != null ? lastRoomCheck.reason() : "No states checked";
            return new JobFacts(
                    0,
                    "Town lacks required job site (or descendant) of: " + dj.location().baseRoom() + " (" + roomReason + ")",
                    false,
                    dj.location().baseRoom().toString(),
                    -1,
                    null,
                    null
            );
        }
        ServerLevel sl = Preconditions.checkNotNull(t.getServerLevel());
        // The scan only depends on the job's location (not on the state), and it does not change
        // within one scoring pass, so it is computed once per job and reused across all states.
        @Nullable List<ContainerTarget<MCContainer, MCTownItem>> jobSiteContainers = null;
        for (int i = 0; i < dj.getMaxState(); i++) {
            int ii = i;
            boolean townHasIngredient = true;
            final IPredicateCollection<MCHeldItem> ing = dj.getChecks().getIngredientsForStep(ii);
            if (ing != null && !ing.isEmpty()) {
                townHasIngredient = false;
                if (jobSiteContainers == null) {
                    jobSiteContainers = Containers.get(
                            t,
                            r -> true,
                            bp -> isJobBlock(t, dj, bp, sl),
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

    /**
     * A town "has" a tool if it is in any town container <em>or in any townie's inventory</em>.
     * The container-only check scored a worker holding the town's only tool as unequipped, so
     * warp reassigned them away from the job they'd do live (the held-tool warp regression
     * scenario). The hand is the first inventory slot; the whole journal inventory is checked
     * because tools can sit in later slots too.
     */
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

    private static boolean isJobBlock(
            TownFlagBlockEntity t,
            DeclarativeJob dj,
            BlockPos bp,
            ServerLevel sl
    ) {
        return dj.location().isJobBlock()
                 .test(new JobBlockTestContext(new MinecraftWorldAccess(sl), info(sl), bp, ImmutableList::of, unique(t), false, false));
    }

    private static Supplier<? extends Collection<Item>> unique(TownFlagBlockEntity t) {
        return () -> TownContainers.getUniqueItems(t);
    }

    public ImmutableList<JobID> getFor(JobID jobId) {
        return UtilClean.getOrDefaultCollection(preselectedJobs, jobId.rootId(), ImmutableList.of());
    }

    /**
     * Dev/observation: score every regular job of a root and return each job's
     * id, score and the reason the gate scanner stopped (missing room / lacking
     * tools or ingredients for state N), plus the current job-board requests.
     * Mirrors what {@link #applyScores} sees, without needing the debug-log toggle.
     */
    // DEV-DIAG: raw_food hunt investigation, 2026-10-02 — grep 'DEV-DIAG' to enumerate; keep/remove per handoff
    public String scoreDump(String root) {
        TownFlagBlockEntity t = town.getUnsafe();
        StringBuilder b = new StringBuilder();
        b.append("preselected=").append(getFor(new JobID(root, "x"))).append("\n");
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
        // The precomputed list is the town-wide "known" set (all of the root's jobs); gate it to
        // the works *this* townie has unlocked for itself, so a freshly-minted townie only ever
        // sees the one work it holds rather than every tier another townie has unlocked.
        VillagerHolder vh = town.getUnsafe().getVillagerHandle();
        ImmutableList<JobID> possible = getFor(currentJob).stream()
                .filter(jobID -> vh.isUnlocked(villagerUUID, jobID))
                .collect(ImmutableList.toImmutableList());
        return cycler.nextValid(possible, canAlwaysStart, canFitInDay, requestedResults, td);
    }

    public void invalidate() {
        shouldRecompute = true;
    }
}
