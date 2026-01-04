package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.Warper;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImportantTicksTest {

    @Test
    public void testShouldReturnCorrectTicksForWhenWindowIsDoubleDuration() {
        JobID jobID = new JobID("test", "gatherer");
        TownFlagState.Work w = new TownFlagState.Work() {
            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime
            ) {
                return jobID;
            }

            @Override
            public long getTotalDuration(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Left town for some ticks
            }
        };
        ImportantTicks.Config cfg = new ImportantTicks.Config(100);

        VillagerUUID vuid = VillagerUUID.random();
        ImmutableList<Warper.Tick> ticks = ImportantTicks.forVillager(w, vuid, jobID, id -> false, cfg, 10, 0L);
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(5L).after(5L),
                Warper.Tick.at(10L).after(5L)
        );
        Assertions.assertEquals(expected, ticks);
    }

    @Test
    public void testShouldReturnCorrectTicksForWhenWindowIsLessThanDoubleDuration() {
        JobID jobID = new JobID("test", "gatherer");
        TownFlagState.Work w = new TownFlagState.Work() {
            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime
            ) {
                return jobID;
            }

            @Override
            public long getTotalDuration(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Left town for some ticks
            }
        };
        int window = 7;
        ImportantTicks.Config cfg = new ImportantTicks.Config(100);

        VillagerUUID vuid = VillagerUUID.random();
        ImmutableList<Warper.Tick> ticks = ImportantTicks.forVillager(w, vuid, jobID, id -> false, cfg, window, 0L);
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(5L).after(5L),
                Warper.Tick.at(7L).after(2L)
        );
        Assertions.assertEquals(expected, ticks);
    }
    @Test
    public void testShouldReturnCorrectTicksForWhenWindowIsMoreThanDoubleDuration() {
        JobID jobID = new JobID("test", "gatherer");
        TownFlagState.Work w = new TownFlagState.Work() {
            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime
            ) {
                return jobID;
            }

            @Override
            public long getTotalDuration(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Left town for some ticks
            }
        };
        int window = 11;
        ImportantTicks.Config cfg = new ImportantTicks.Config(100);

        VillagerUUID vuid = VillagerUUID.random();
        ImmutableList<Warper.Tick> ticks = ImportantTicks.forVillager(w, vuid, jobID, id -> false, cfg, window, 0L);
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(5L).after(5L),
                Warper.Tick.at(10L).after(5L),
                Warper.Tick.at(11L).after(1L)
        );
        Assertions.assertEquals(expected, ticks);
    }

    @Test
    public void testShouldReturnCorrectTicksWhenStartInDowntime() {
        JobID downID = new JobID("test", "downtime");
        TownFlagState.Work w = new TownFlagState.Work() {
            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime
            ) {
                return new JobID("test", "gatherer");
            }

            @Override
            public long getTotalDuration(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Left town for some ticks
            }
        };
        int window = 11;
        ImportantTicks.Config cfg = new ImportantTicks.Config(2);

        VillagerUUID vuid = VillagerUUID.random();
        ImmutableList<Warper.Tick> ticks = ImportantTicks.forVillager(w, vuid, downID, downID::equals, cfg, window, 0L);
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(2L).after(2L),
                Warper.Tick.at(7L).after(5L),
                Warper.Tick.at(11L).after(4L)
        );
        Assertions.assertEquals(expected, ticks);
    }

}