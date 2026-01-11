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
    // Default work cycle duration when job is unknown (will be resolved dynamically)
    private static final long DEFAULT_WORK_CYCLE_TICKS = 1000;

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
            int ticksPerCycle = 5;
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
            int ticksPerCycle = 5;
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
        // COLLECTING_SUPPLIES, work states, EXTRACTING_PRODUCT, DROPPING_LOOT
        // We add 5 ticks per cycle (spaced 1 tick apart) to ensure all transitions happen
        int ticksPerCycle = 5;
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
