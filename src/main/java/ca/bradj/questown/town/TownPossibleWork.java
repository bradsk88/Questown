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
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import ca.bradj.questown.world.MinecraftWorldAccess;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
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
            scoringInProgress.scored.add(new JobPossibility(job.getKey(), getWorkPercentPossible(t, job)));
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
            registerUnmetNeeds(root);
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

    private void registerUnmetNeeds(String root) {
        try {
            ServerLevel sl = town.getServerLevelUnsafe();
            long tick = Util.getTick(sl);
            VillagerHolder vh = town.getUnsafe().getVillagerHandle();
            Work work = ServerJobsRegistry.getRandomWork(sl, root, vh::isUnlocked);
            NoMCEconomics econ = town.getUnsafe().getEconomicsHandle();
            vh.entities().stream().map(v -> (VisitorMobEntity) v).filter(v -> root.equals(v.getJobId().rootId()))
              .forEach(villager -> this.registerUnmetNed(work, econ, tick, villager));
        } catch (Exception e) {
            QT.FLAG_LOGGER.error("Failed to register unmet needs for root: {}", root, e);
        }
    }

    private void registerUnmetNed(
            Work work,
            NoMCEconomics econ,
            long tick,
            VisitorMobEntity villager
    ) {
        UUID uuid = villager.getUUID();
        DeclarativeJob x = (DeclarativeJob) work.jobFunc.apply(uuid);
        Ingredient xx = x.initialIngredients.get(0);
        if (xx == null) {
            xx = x.initialTools.get(0);
            if (xx == null) {
                return;
            }
        }
        villager.setNeed(TownieNeed.UNMET_ITEM);
        econ.registerUnmetNeed(tick, uuid, Ingredients.toString(xx));
    }

    /** A townie with viable work again is no longer waiting on supplies. */
    private void clearUnmetItemNeeds(String root) {
        try {
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
            WithReason<Double> score
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

    private static WithReason<Double> getWorkPercentPossible(
            TownFlagBlockEntity t,
            Map.Entry<JobID, Supplier<Work>> w
    ) {
        Work work = w.getValue().get();
        Job<?, ?, ?> j = work.jobFunc.apply(UUID.randomUUID());
        if (!(j instanceof DeclarativeJob dj)) {
            return WithReason.always(0.0, "Unsupported job class " + j.getClass().getName());
        }

        dj.initialize(t.getServerLevel(), dj.getJournalSnapshot());

        // Check if product is requested
        String requestStatus = getRequestStatus(t, j.getId());

        WithReason<Integer> hps = getHighestPossibleState(t, dj);
        double v = (double) hps.value / dj.getMaxState();
        float shuffler = Compat.nextRandomInt(t.getServerLevel(), 100) / 10000f;
        return WithReason.always(
                v + shuffler,
                "Highest possible job state: " + hps + " (out of " + dj.getMaxState() + ", with randomizer " + shuffler + ")" + requestStatus
        );
    }

    private static String getRequestStatus(TownFlagBlockEntity t, JobID jobId) {
        ImmutableList<ca.bradj.questown.jobs.requests.WorkRequest> requests = t.getWorkHandle().getRequestedResults();
        if (requests.isEmpty()) {
            return " [No items requested on job board]";
        }
        WorksBehaviour.TownData td = t.getTownData();
        for (ca.bradj.questown.jobs.requests.WorkRequest r : requests) {
            if (ServerJobsRegistry.canSatisfy(td, jobId, r.asIngredient())) {
                return " [Satisfies request: " + r + "]";
            }
        }
        return " [Product not requested]";
    }

    private static WithReason<Integer> getHighestPossibleState(
            TownFlagBlockEntity t,
            DeclarativeJob dj
    ) {
        if (dj.specialGlobalRules.contains(SpecialRules.ALWAYS_CONSIDER)) {
            return WithReason.always(dj.getMaxState(), "Special rule ALWAYS_CONSIDER present");
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
            return WithReason.always(0, "Town lacks required job site (or descendant) of: " + dj.location().baseRoom() + " (" + roomReason + ")");
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
                townHasTool = false;
                @Nullable ContainerTarget<MCContainer, MCTownItem> toolCont = t.findMatchingContainer(tool::test);
                if (toolCont != null) {
                    townHasTool = true;
                }
            }

            if (!townHasIngredient || !townHasTool) {
                int max = Math.max(0, i - 1);
                return WithReason.always(
                        max,
                        "Lacking " +
                                (townHasIngredient ? "" : "ingredients ") +
                                (townHasTool ? "" : "tools ") +
                                "for job state " + ii + "/" + dj.getMaxState()
                );
            }
        }
        return WithReason.always(
                dj.getMaxState(),
                "All ingredients and tools available for all job states"
        );
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

    public @Nullable JobID nextForVillager(
            UUID villagerUUID,
            JobID currentJob,
            Predicate<JobID> canAlwaysStart,
            Predicate<JobID> canFitInDay,
            ImmutableList<WorkRequest> requestedResults,
            WorksBehaviour.TownData td
    ) {
        JobCycler cycler = villagerCyclers.computeIfAbsent(villagerUUID, k -> new JobCycler());
        return cycler.nextValid(getFor(currentJob), canAlwaysStart, canFitInDay, requestedResults, td);
    }

    public void invalidate() {
        shouldRecompute = true;
    }
}
