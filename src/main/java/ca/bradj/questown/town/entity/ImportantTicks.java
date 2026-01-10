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
    public record Config(
            long MAX_DOWNTIME_TICKS
    ) {}

    public static ImmutableList<Warper.Tick> forVillager(
            TownFlagState.Work w,
            VillagerUUID uuid,
            JobID jobID,
            Predicate<JobID> isDowntime,
            Config config,
            long ticksPassed,
            long startingAtGameTime
    ) {
//        Warper<ServerLevel, MCTownState> vWarper = ServerJobsRegistry.getWarper(
//                i, jobID
//        );

        Collection<Warper.Tick> ticks = new ArrayList<>();
        if (isDowntime.test(jobID)) {
            startingAtGameTime += config.MAX_DOWNTIME_TICKS;
            ticksPassed -= config.MAX_DOWNTIME_TICKS;
            ticks.add(new Warper.Tick(config.MAX_DOWNTIME_TICKS, config.MAX_DOWNTIME_TICKS));
        }

//        AtomicLong ticksLeftToPass = new AtomicLong(ticksPassed);
//        AtomicReference<JobID> jobFound = new AtomicReference<>(null);
//        UtilClean.whileOrLimit(() -> ticksLeftToPass.get() > 0, () -> {
//            w.recomputeNow();
//            e.getStartableWork().recomputeNow(sl, PossibilitySources.from(e));
//            jobFound.set(e.getRandomFinishableWork(jobID, Signals.DayTime.fromGameTime(dayTime), false));
//        });

        w.recomputeNow();
        jobID = w.getRandomFinishableWork(jobID, Signals.DayTime.fromGameTime(startingAtGameTime));

        if (jobID == null) {
            // No finishable work available - return just the ticks collected so far
            return ImmutableList.copyOf(ticks);
        }

        // TODO[Warp]: Factor MAX_TICKS_BETWEEN_DOWNTIME into the section below
        //  i.e. Every MAX_TICKS_BETWEEN_DOWNTIME, we should skip MAX_DOWNTIME_TICKS
        //  to simulate villagers taking breaks.

        long totalDuration = w.getTotalDuration(jobID, uuid);
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
        return ImmutableList.copyOf(ticks);
    }
}
