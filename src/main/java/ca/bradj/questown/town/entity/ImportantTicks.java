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

        // TODO[Warp]: Factor MAX_TICKS_BETWEEN_DOWNTIME into the section below
        //  i.e. Every MAX_TICKS_BETWEEN_DOWNTIME, we should skip MAX_DOWNTIME_TICKS
        //  to simulate villagers taking breaks.

        long totalDuration = w.getTotalDuration(jobID, uuid);
        long prev = 0;
        for (int j = 0; j <= ticksPassed; j+= (int) totalDuration) {
            long tick = Math.min(j + totalDuration, ticksPassed);
            ticks.add(new Warper.Tick(startingAtGameTime + tick, tick - prev));
            if (tick == ticksPassed) {
                break;
            }
            prev = tick;
        }
        return ImmutableList.copyOf(ticks);
    }
}
