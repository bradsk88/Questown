package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.town.entity.TownFlagState;
import ca.bradj.questown.world.QTWorldAccess;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Warper for villagers who were in downtime when warp started.
 * At each warp tick, resolves what work is available and delegates to that job's warper.
 *
 * Uses deterministic cycling through preselected jobs instead of random selection,
 * ensuring every sub-job gets a turn before any repeats.
 */
public class PostDowntimeWarper implements Warper<ServerLevel, MCTownState> {

    private final TownFlagState.Work work;
    private final JobID fallbackJobID;
    private final int villagerIndex;
    private final BlockPos townFlagPos;
    private final Collection<BlockPos> roomPositions;
    private final @Nullable QTWorldAccess warpWorld;

    public PostDowntimeWarper(
            TownFlagState.Work work,
            JobID fallbackJobID,
            int villagerIndex,
            BlockPos townFlagPos,
            Collection<BlockPos> roomPositions,
            @Nullable QTWorldAccess warpWorld
    ) {
        this.work = work;
        this.fallbackJobID = fallbackJobID;
        this.villagerIndex = villagerIndex;
        this.townFlagPos = townFlagPos;
        this.roomPositions = roomPositions;
        this.warpWorld = warpWorld;
    }

    private static final int MAX_STICKY_TICKS = 5;

    private List<JobID> cycleJobs = null;
    private int cycleIndex = 0;
    private JobID cachedJob = null;
    private int stickyTicks = 0;
    private final WarpCycleBudget cycleBudget = new WarpCycleBudget();
    // A job resolved while the budget could not yet afford it; kept so the tier rotation does
    // not skip it while the villager "waits out" the time.
    private @Nullable JobID unaffordedJob = null;
    private final Map<JobID, @Nullable BlockPos> assignedWorkBlocks = new HashMap<>();

    public JobID resolveJob(long ticksPassed, boolean villagerHasItems) {
        if (cycleJobs == null) {
            work.recomputeNow();
            cycleJobs = new ArrayList<>(work.getPreselectedJobs(fallbackJobID));
            cycleJobs.removeIf(ServerJobsRegistry::isExcludedFromWarp);
            Collections.shuffle(cycleJobs);
            cycleIndex = 0;
        }

        if (cachedJob != null && villagerHasItems && stickyTicks < MAX_STICKY_TICKS) {
            stickyTicks++;
            return cachedJob;
        }

        stickyTicks = 0;

        if (cycleJobs.isEmpty()) {
            cachedJob = fallbackJobID;
            return cachedJob;
        }

        cachedJob = cycleJobs.get(cycleIndex % cycleJobs.size());
        cycleIndex++;
        return cachedJob;
    }

    // For testing without MCTownState
    public JobID resolveJob(long ticksPassed) {
        return resolveJob(ticksPassed, cachedJob != null);
    }

    /**
     * Finds the room blocks the job's own block-identity check matches (the job's container
     * block — a furnace for the cook family, the bread oven for the baker family). A job whose
     * block does not occur in any room gets no assigned work block, and its warper works at a
     * virtual position as before. The identity check is content-agnostic (no slot gate): the
     * warp is what fills the slots, so gating the assignment on slot contents would leave a
     * take-job like {@code baker/extract} unassigned for the whole warp. With several matching
     * blocks the villager is round-robined onto one, matching the old furnace assignment.
     * The value only matters for jobs with slot rules; {@code TimeWarpWorldInteraction
     * .shouldUseRealWorkBlock()} gates on it.
     */
    @Nullable BlockPos findAssignedWorkBlock(ServerLevel level, JobID job) {
        if (assignedWorkBlocks.containsKey(job)) {
            return assignedWorkBlocks.get(job);
        }
        List<BlockPos> matches = new ArrayList<>();
        for (BlockPos pos : roomPositions) {
            if (ServerJobsRegistry.jobBlockAt(job, level, pos)) {
                matches.add(pos);
            }
        }
        @Nullable BlockPos found = matches.isEmpty()
                ? null
                : matches.get(villagerIndex % matches.size());
        assignedWorkBlocks.put(job, found);
        if (found != null) {
            QT.FLAG_LOGGER.debug(
                    "[PostDowntimeWarper] Assigned work block {} to villager {} for job {} (of {} matches)",
                    found, villagerIndex, job, matches.size()
            );
        }
        return found;
    }

    @Override
    public MCTownState warp(
            ServerLevel level,
            MCTownState liveState,
            long currentTick,
            long ticksPassed,
            int villagerNum
    ) {
        cycleBudget.credit(ticksPassed);
        boolean villagerHasItems = liveState.villagers.get(villagerIndex).journal.items().stream()
                .anyMatch(item -> !item.isEmpty());
        JobID resolvedJob = unaffordedJob != null ? unaffordedJob : resolveJob(ticksPassed, villagerHasItems);
        unaffordedJob = null;

        if (resolvedJob == null) {
            return liveState;
        }

        @Nullable BlockPos workBlock = findAssignedWorkBlock(level, resolvedJob);

        Warper<ServerLevel, MCTownState> jobWarper = ServerJobsRegistry.getWarper(
                villagerIndex,
                resolvedJob,
                townFlagPos,
                roomPositions,
                workBlock,
                warpWorld
        );

        if (jobWarper == NoOpWarper.INSTANCE) {
            return liveState;
        }

        // Duration is a property of the job, not its owner (ServerJobsRegistry.canFit also
        // resolves it with a null villager).
        long cycleDuration = work.getTotalDuration(resolvedJob, null);
        if (!cycleBudget.canStart(cycleDuration)) {
            unaffordedJob = resolvedJob;
            return liveState;
        }

        MCTownState result = runToCompletion(resolvedJob, jobWarper, level, liveState, currentTick, ticksPassed, villagerNum);
        if (jobWarper.isCycleComplete()) {
            cycleBudget.spend(cycleDuration);
        }
        if (result == liveState) {
            stickyTicks = MAX_STICKY_TICKS;
        }
        return result;
    }

    private static MCTownState runToCompletion(
            JobID jobId,
            Warper<ServerLevel, MCTownState> warper,
            ServerLevel level,
            MCTownState state,
            long currentTick,
            long ticksPassed,
            int villagerNum
    ) {
        for (int i = 0; i < 64; i++) {
            MCTownState next = warper.warp(level, state, currentTick, ticksPassed, villagerNum);
            if (next == state) {
                break;
            }
            state = next;
            if (warper.isCycleComplete()) {
                break;
            }
        }
        return state;
    }

    @Override
    public Collection<Tick> getTicks(long referenceTick, long ticksPassed) {
        // Ticks are computed by ImportantTicks, not by the warper
        return ImmutableList.of();
    }
}
