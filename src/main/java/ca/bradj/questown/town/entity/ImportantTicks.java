package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public class ImportantTicks {
    // Default work cycle duration when job is unknown (will be resolved dynamically).
    // This accounts for real-time overhead: walking, pathfinding, container access, etc.
    // Real-time observation: ~6 cycles in 10,000 ticks = ~1,666 ticks per cycle.
    private static final long DEFAULT_WORK_CYCLE_TICKS = 1666;

    // Default ticks per cycle for dynamic resolution (when job isn't known ahead of time).
    // This should match typical crafting jobs to avoid over/under production.
    // Most crafting jobs need:
    // - Ingredient collection: 2-3 items
    // - Work required: 5-10 work units
    // - Overhead: 2 (extract + drop)
    // Total: ~14 ticks per cycle is a reasonable default
    private static final int DEFAULT_DYNAMIC_TICKS_PER_CYCLE = 14;

    public record Config(
            long MAX_DOWNTIME_TICKS
    ) {}

    public record Result(
            ImmutableList<Warper.Tick> ticks,
            boolean useDynamicResolution
    ) {}

    public static Result forVillager(
            TownFlagState.Work w,
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

        // TODO[Warp]: Factor MAX_TICKS_BETWEEN_DOWNTIME into the section below
        //  i.e. Every MAX_TICKS_BETWEEN_DOWNTIME, we should skip MAX_DOWNTIME_TICKS
        //  to simulate villagers taking breaks.

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
