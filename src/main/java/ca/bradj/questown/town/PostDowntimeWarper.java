package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.entity.TownFlagState;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;

/**
 * Warper for villagers who were in downtime when warp started.
 * At each warp tick, resolves what work is available and delegates to that job's warper.
 */
public class PostDowntimeWarper implements Warper<ServerLevel, MCTownState> {

    private final TownFlagState.Work work;
    private final JobID fallbackJobID;
    private final int villagerIndex;
    private final BlockPos townFlagPos;

    public PostDowntimeWarper(
            TownFlagState.Work work,
            JobID fallbackJobID,
            int villagerIndex,
            BlockPos townFlagPos
    ) {
        this.work = work;
        this.fallbackJobID = fallbackJobID;
        this.villagerIndex = villagerIndex;
        this.townFlagPos = townFlagPos;
    }

    // During warp simulation, we use a virtual morning time to ensure jobs aren't
    // rejected due to "not enough time left in the day". The actual game time when
    // warp happens is irrelevant - we're simulating work across a period of time.
    private static final long VIRTUAL_MORNING_TICK = 1000; // ~1am, plenty of daytime ahead

    private boolean hasRecomputed = false;
    private JobID cachedJob = null;

    /**
     * Resolves which job the villager should work on.
     * Job stays consistent while villager has items (mid-cycle).
     * When inventory is empty (cycle complete), a new job can be selected.
     */
    public JobID resolveJob(long ticksPassed, boolean villagerHasItems) {
        if (!hasRecomputed) {
            work.recomputeNow();
            hasRecomputed = true;
        }

        // If villager has items, they're mid-cycle - stick with current job
        if (cachedJob != null && villagerHasItems) {
            return cachedJob;
        }

        // Villager inventory is empty - allow job re-evaluation
        cachedJob = work.getRandomFinishableWork(
                fallbackJobID,
                new Signals.DayTime(VIRTUAL_MORNING_TICK),
                Math.max(ticksPassed, 1000)
        );
        return cachedJob;
    }

    // For testing without MCTownState
    public JobID resolveJob(long ticksPassed) {
        return resolveJob(ticksPassed, cachedJob != null);
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
        QT.FLAG_LOGGER.debug(
                "[PostDowntimeWarper] tick={} villagerHasItems={} cachedJob={} items={}",
                currentTick, villagerHasItems, cachedJob,
                liveState.villagers.get(villagerIndex).journal.items().stream()
                        .filter(item -> !item.isEmpty())
                        .map(item -> item.toShortString())
                        .toList()
        );
        JobID resolvedJob = resolveJob(ticksPassed, villagerHasItems);

        if (resolvedJob == null) {
            QT.FLAG_LOGGER.debug(
                    "[PostDowntimeWarper] No finishable work found at tick {}, skipping",
                    currentTick
            );
            return liveState;
        }

        QT.FLAG_LOGGER.debug(
                "[PostDowntimeWarper] Resolved job {} at tick {}",
                resolvedJob,
                currentTick
        );


        // Get the warper for the resolved job and delegate
        Warper<ServerLevel, MCTownState> jobWarper = ServerJobsRegistry.getWarper(
                villagerIndex,
                resolvedJob,
                townFlagPos
        );

        // If the resolved job also returns NoOpWarper, don't recurse infinitely
        if (jobWarper instanceof NoOpWarper) {
            return liveState;
        }

        return jobWarper.warp(level, liveState, currentTick, ticksPassed, villagerNum);
    }

    @Override
    public Collection<Tick> getTicks(long referenceTick, long ticksPassed) {
        // Generate ticks at regular intervals for checking work
        // The actual job's tick spacing will be used once we delegate
        return ImmutableList.of();
    }
}
