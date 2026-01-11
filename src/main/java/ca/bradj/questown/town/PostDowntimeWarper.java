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

    @Override
    public MCTownState warp(
            ServerLevel level,
            MCTownState liveState,
            long currentTick,
            long ticksPassed,
            int villagerNum
    ) {
        // Only recompute once per warp session, not on every tick
        // This prevents the expensive job possibility computation from running hundreds of times
        if (!hasRecomputed) {
            work.recomputeNow();
            hasRecomputed = true;
        }

        // Resolve what job the villager can do right now
        // Use a virtual morning time to ensure canFit() doesn't reject jobs during warp.
        // The actual game tick is irrelevant during simulation - we're fast-forwarding
        // through time and want villagers to be productive.
        // Pass a large ticksElapsed to bypass the preferredBuffer throttle in getRandomFinishableWork.
        // During warp, we want immediate job resolution without the normal throttling.
        JobID resolvedJob = work.getRandomFinishableWork(
                fallbackJobID,
                new Signals.DayTime(VIRTUAL_MORNING_TICK),
                Math.max(ticksPassed, 1000) // Ensure buffer check passes immediately during warp
        );

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
