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

        if (isDowntime.test(jobID)) {
            return forDowntimeVillager(ticks, config, ticksPassed, startingAtGameTime);
        }

        w.recomputeNow();
        JobID resolvedJob = w.getRandomFinishableWork(
                jobID, Signals.DayTime.virtualMorning(), ticksPassed
        );

        if (resolvedJob == null) {
            return withDefaultTickSpacing(ticks, ticksPassed, startingAtGameTime, true);
        }

        // totalDuration includes real-world overhead (walking, pathfinding, etc.).
        // Explicit downtime simulation was tested but caused under-production vs real-time.
        long totalDuration = w.getTotalDuration(resolvedJob, uuid);
        if (totalDuration <= 0) {
            totalDuration = DEFAULT_WORK_CYCLE_TICKS;
        }
        int ticksPerCycle = Math.max(w.getWarpTicksPerCycle(resolvedJob, uuid), 5);

        generateTicks(ticks, ticksPassed, startingAtGameTime, totalDuration, ticksPerCycle);
        return new Result(ImmutableList.copyOf(ticks), false);
    }

    private static Result forDowntimeVillager(
            Collection<Warper.Tick> ticks,
            Config config,
            long ticksPassed,
            long startingAtGameTime
    ) {
        startingAtGameTime += config.MAX_DOWNTIME_TICKS;
        ticksPassed -= config.MAX_DOWNTIME_TICKS;
        ticks.add(new Warper.Tick(config.MAX_DOWNTIME_TICKS, config.MAX_DOWNTIME_TICKS));

        if (ticksPassed <= 0) {
            return new Result(ImmutableList.copyOf(ticks), true);
        }

        generateTicks(ticks, ticksPassed, startingAtGameTime,
                DEFAULT_WORK_CYCLE_TICKS, DEFAULT_DYNAMIC_TICKS_PER_CYCLE);
        return new Result(ImmutableList.copyOf(ticks), true);
    }

    private static Result withDefaultTickSpacing(
            Collection<Warper.Tick> ticks,
            long ticksPassed,
            long startingAtGameTime,
            boolean useDynamicResolution
    ) {
        generateTicks(ticks, ticksPassed, startingAtGameTime,
                DEFAULT_WORK_CYCLE_TICKS, DEFAULT_DYNAMIC_TICKS_PER_CYCLE);
        return new Result(ImmutableList.copyOf(ticks), useDynamicResolution);
    }

    private static void generateTicks(
            Collection<Warper.Tick> ticks,
            long ticksPassed,
            long startingAtGameTime,
            long cycleDuration,
            int ticksPerCycle
    ) {
        long stepSpacing = Math.max(1, cycleDuration / ticksPerCycle);
        long prev = 0;
        for (int j = 0; j <= ticksPassed; j += (int) cycleDuration) {
            for (int step = 0; step < ticksPerCycle; step++) {
                long tickOffset = Math.min(j + (long) step * stepSpacing, ticksPassed);
                if (tickOffset > ticksPassed) {
                    break;
                }
                long ticksSince = tickOffset - prev;
                if (ticksSince > 0) {
                    ticks.add(new Warper.Tick(startingAtGameTime + tickOffset, ticksSince));
                    prev = tickOffset;
                }
            }
            if (j + cycleDuration > ticksPassed) {
                break;
            }
        }
    }
}
