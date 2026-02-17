package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.town.entity.TownFlagState;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    public PostDowntimeWarper(
            TownFlagState.Work work,
            JobID fallbackJobID,
            int villagerIndex,
            BlockPos townFlagPos,
            Collection<BlockPos> roomPositions
    ) {
        this.work = work;
        this.fallbackJobID = fallbackJobID;
        this.villagerIndex = villagerIndex;
        this.townFlagPos = townFlagPos;
        this.roomPositions = roomPositions;
    }

    private static final int MAX_STICKY_TICKS = 5;

    private List<JobID> cycleJobs = null;
    private int cycleIndex = 0;
    private JobID cachedJob = null;
    private int stickyTicks = 0;
    private @Nullable BlockPos assignedFurnace;
    private boolean furnaceSearched = false;

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

    private @Nullable BlockPos findAssignedFurnace(ServerLevel level) {
        if (furnaceSearched) {
            return assignedFurnace;
        }
        furnaceSearched = true;
        List<BlockPos> furnaces = new ArrayList<>();
        for (BlockPos pos : roomPositions) {
            if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
                furnaces.add(pos);
            }
        }
        if (furnaces.isEmpty()) {
            return null;
        }
        assignedFurnace = furnaces.get(villagerIndex % furnaces.size());
        QT.FLAG_LOGGER.debug(
                "[PostDowntimeWarper] Assigned furnace {} to villager {} (of {} furnaces)",
                assignedFurnace, villagerIndex, furnaces.size()
        );
        return assignedFurnace;
    }

    @Override
    public MCTownState warp(
            ServerLevel level,
            MCTownState liveState,
            long currentTick,
            long ticksPassed,
            int villagerNum
    ) {
        boolean villagerHasItems = liveState.villagers.get(villagerIndex).journal.items().stream()
                .anyMatch(item -> !item.isEmpty());
        JobID resolvedJob = resolveJob(ticksPassed, villagerHasItems);

        if (resolvedJob == null) {
            return liveState;
        }

        @Nullable BlockPos furnace = findAssignedFurnace(level);

        Warper<ServerLevel, MCTownState> jobWarper = ServerJobsRegistry.getWarper(
                villagerIndex,
                resolvedJob,
                townFlagPos,
                roomPositions,
                furnace
        );

        if (jobWarper instanceof NoOpWarper) {
            return liveState;
        }

        MCTownState result = runToCompletion(resolvedJob, jobWarper, level, liveState, currentTick, ticksPassed, villagerNum);
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
