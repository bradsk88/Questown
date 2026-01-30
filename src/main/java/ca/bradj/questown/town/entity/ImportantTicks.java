package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.AbstractAdvanceTime;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

/**
 * Computes which game ticks are "important" for a villager's work during time warp.
 * This class has no Minecraft dependencies and can be unit tested.
 */
public class ImportantTicks {

    /**
     * Adapts TownFlagState.Work to AbstractAdvanceTime.Work.
     */
    public static AbstractAdvanceTime.Work adaptWork(TownFlagState.Work w) {
        return new AbstractAdvanceTime.Work() {
            @Override
            public void recomputeNow() {
                w.recomputeNow();
            }

            @Override
            public JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime,
                    long ticksElapsed
            ) {
                return w.getRandomFinishableWork(jobID, dayTime, ticksElapsed);
            }

            @Override
            public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
                return w.getTotalDuration(jobID, vuid);
            }

            @Override
            public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
                return w.getWarpTicksPerCycle(jobID, vuid);
            }
        };
    }
    // Default work cycle duration when job is unknown (will be resolved dynamically).
    // This accounts for real-time overhead: walking, pathfinding, container access, etc.
    // Real-time observation: ~4 cycles in 10,000 ticks = ~2,500 ticks per cycle.
    private static final long DEFAULT_WORK_CYCLE_TICKS = 2500;

    // Default ticks per cycle for dynamic resolution (when job isn't known ahead of time).
    // Each substep can advance production state, so this controls how many state transitions
    // happen per work cycle. Too high = overproduction, too low = underproduction.
    // Real-time observation: ~4 cycles in 10,000 ticks with bowl crafting
    // Bowl crafting needs: 2 ingredients + 10 work + 2 overhead = 14 steps per cycle
    // With 4 cycle groups over 10,000 ticks, we need ~14 substeps per group (not 140)
    private static final int DEFAULT_DYNAMIC_TICKS_PER_CYCLE = 28;

    public record Config(
            long MAX_DOWNTIME_TICKS
    ) {}

    public record Result(
            ImmutableList<Warper.Tick> ticks,
            boolean useDynamicResolution
    ) {}

    public static Result forVillager(
            AbstractAdvanceTime.Work w,
            VillagerUUID uuid,
            JobID jobID,
            Predicate<JobID> isDowntime,
            Config config,
            long ticksPassed,
            long startingAtGameTime
    ) {
        Collection<Warper.Tick> ticks = new ArrayList<>();
        boolean wasOnDowntime = isDowntime.test(jobID);

        if (wasOnDowntime) {
            // Skip downtime period
            startingAtGameTime += config.MAX_DOWNTIME_TICKS;
            ticksPassed -= config.MAX_DOWNTIME_TICKS;
            ticks.add(new Warper.Tick(config.MAX_DOWNTIME_TICKS, config.MAX_DOWNTIME_TICKS));

            if (ticksPassed <= 0) {
                // Only downtime, no work time remaining
                return new Result(ImmutableList.copyOf(ticks), true);
            }

            // For downtime villagers, generate ticks at default intervals
            // The DynamicJobWarper will resolve the actual job at each tick
            long totalDuration = DEFAULT_WORK_CYCLE_TICKS;
            // Use higher default for dynamic resolution since we don't know the job's requirements
            int ticksPerCycle = DEFAULT_DYNAMIC_TICKS_PER_CYCLE;
            long prev = 0;
            for (int j = 0; j <= ticksPassed; j += (int) totalDuration) {
                long baseTick = j + totalDuration;

                for (int step = 0; step < ticksPerCycle; step++) {
                    long tickOffset = Math.min(baseTick + step, ticksPassed);
                    if (tickOffset > ticksPassed) {
                        break;
                    }
                    long ticksSince = tickOffset - prev;
                    if (ticksSince > 0) {
                        ticks.add(new Warper.Tick(startingAtGameTime + tickOffset, ticksSince));
                        prev = tickOffset;
                    }
                }

                if (baseTick >= ticksPassed) {
                    break;
                }
            }
            return new Result(ImmutableList.copyOf(ticks), true);
        }

        // Not on downtime - use existing job to compute tick spacing
        w.recomputeNow();
        // Pass a large ticksElapsed to bypass the preferredBuffer check during warp.
        // Use a virtual morning time to ensure canFit() doesn't reject jobs during warp.
        // The actual game tick when warp happens is irrelevant for tick spacing calculation.
        JobID resolvedJob = w.getRandomFinishableWork(jobID, new Signals.DayTime(1000), ticksPassed);

        if (resolvedJob == null) {
            // Current job can't be completed and no alternative work found.
            // Fall back to dynamic resolution so villager can find other work during warp.
            long totalDuration = DEFAULT_WORK_CYCLE_TICKS;
            // Use higher default for dynamic resolution since we don't know the job's requirements
            int ticksPerCycle = DEFAULT_DYNAMIC_TICKS_PER_CYCLE;
            long prev = 0;
            for (int j = 0; j <= ticksPassed; j += (int) totalDuration) {
                long baseTick = j + totalDuration;

                for (int step = 0; step < ticksPerCycle; step++) {
                    long tickOffset = Math.min(baseTick + step, ticksPassed);
                    if (tickOffset > ticksPassed) {
                        break;
                    }
                    long ticksSince = tickOffset - prev;
                    if (ticksSince > 0) {
                        ticks.add(new Warper.Tick(startingAtGameTime + tickOffset, ticksSince));
                        prev = tickOffset;
                    }
                }

                if (baseTick >= ticksPassed) {
                    break;
                }
            }
            // Use dynamic resolution since we couldn't find work with the current job
            return new Result(ImmutableList.copyOf(ticks), true);
        }

        // NOTE: Downtime is already accounted for in the totalDuration calculation
        // which includes real-world overhead (walking, pathfinding, etc.).
        // Explicit downtime simulation was tested but caused under-production vs real-time.

        long totalDuration = w.getTotalDuration(resolvedJob, uuid);
        // Guard against infinite loop if duration is 0 or negative
        if (totalDuration <= 0) {
            totalDuration = DEFAULT_WORK_CYCLE_TICKS;
        }
        // Each work cycle needs multiple ticks for status transitions:
        // - Ingredient collection (1 tick per ingredient)
        // - Work ticks (1 tick per work unit)
        // - Extraction and dropping (2 ticks overhead)
        // Use the job's calculated value instead of hardcoded 5
        int ticksPerCycle = w.getWarpTicksPerCycle(resolvedJob, uuid);
        // Ensure minimum of 5 ticks for safety
        ticksPerCycle = Math.max(ticksPerCycle, 5);
        long prev = 0;
        for (int j = 0; j <= ticksPassed; j += (int) totalDuration) {
            long baseTick = j + totalDuration;

            for (int step = 0; step < ticksPerCycle; step++) {
                long tickOffset = Math.min(baseTick + step, ticksPassed);
                if (tickOffset > ticksPassed) {
                    break;
                }
                long ticksSince = tickOffset - prev;
                if (ticksSince > 0) {
                    ticks.add(new Warper.Tick(startingAtGameTime + tickOffset, ticksSince));
                    prev = tickOffset;
                }
            }

            if (baseTick >= ticksPassed) {
                break;
            }
        }
        return new Result(ImmutableList.copyOf(ticks), false);
    }
}
