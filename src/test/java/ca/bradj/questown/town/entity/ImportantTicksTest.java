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
                    Signals.DayTime dayTime,
                    long ticksElapsed
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

            @Override
            public int getWarpTicksPerCycle(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Default test value
            }
        };
        ImportantTicks.Config cfg = new ImportantTicks.Config(100);

        VillagerUUID vuid = VillagerUUID.random();
        ImportantTicks.Result result = ImportantTicks.forVillager(w, vuid, jobID, id -> false, cfg, 10, 0L);
        // Each work cycle generates 5 ticks (for status transitions)
        // Duration=5, window=10: first cycle at tick 5 (5,6,7,8,9), second at tick 10
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(5L).after(5L),
                Warper.Tick.at(6L).after(1L),
                Warper.Tick.at(7L).after(1L),
                Warper.Tick.at(8L).after(1L),
                Warper.Tick.at(9L).after(1L),
                Warper.Tick.at(10L).after(1L)
        );
        Assertions.assertEquals(expected, result.ticks());
        Assertions.assertFalse(result.useDynamicResolution());
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
                    Signals.DayTime dayTime,
                    long ticksElapsed
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

            @Override
            public int getWarpTicksPerCycle(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Default test value
            }
        };
        int window = 7;
        ImportantTicks.Config cfg = new ImportantTicks.Config(100);

        VillagerUUID vuid = VillagerUUID.random();
        ImportantTicks.Result result = ImportantTicks.forVillager(w, vuid, jobID, id -> false, cfg, window, 0L);
        // Duration=5, window=7: first cycle at tick 5 generates ticks 5,6,7 (capped at window)
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(5L).after(5L),
                Warper.Tick.at(6L).after(1L),
                Warper.Tick.at(7L).after(1L)
        );
        Assertions.assertEquals(expected, result.ticks());
        Assertions.assertFalse(result.useDynamicResolution());
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
                    Signals.DayTime dayTime,
                    long ticksElapsed
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

            @Override
            public int getWarpTicksPerCycle(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Default test value
            }
        };
        int window = 11;
        ImportantTicks.Config cfg = new ImportantTicks.Config(100);

        VillagerUUID vuid = VillagerUUID.random();
        ImportantTicks.Result result = ImportantTicks.forVillager(w, vuid, jobID, id -> false, cfg, window, 0L);
        // Duration=5, window=11: first cycle at tick 5 (5,6,7,8,9), second cycle at tick 10 (10,11)
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(5L).after(5L),
                Warper.Tick.at(6L).after(1L),
                Warper.Tick.at(7L).after(1L),
                Warper.Tick.at(8L).after(1L),
                Warper.Tick.at(9L).after(1L),
                Warper.Tick.at(10L).after(1L),
                Warper.Tick.at(11L).after(1L)
        );
        Assertions.assertEquals(expected, result.ticks());
        Assertions.assertFalse(result.useDynamicResolution());
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
                    Signals.DayTime dayTime,
                    long ticksElapsed
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

            @Override
            public int getWarpTicksPerCycle(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                return 5; // Default test value
            }
        };
        int window = 3500; // Enough for downtime + 2 work cycles at DEFAULT_WORK_CYCLE_TICKS (1666)
        ImportantTicks.Config cfg = new ImportantTicks.Config(100);

        VillagerUUID vuid = VillagerUUID.random();
        ImportantTicks.Result result = ImportantTicks.forVillager(w, vuid, downID, downID::equals, cfg, window, 0L);
        // With downtime=100 and window=3500, we have 3400 ticks for work
        // At DEFAULT_WORK_CYCLE_TICKS=1666, we get cycles at 1666 and 3332
        // With DEFAULT_DYNAMIC_TICKS_PER_CYCLE=14, first cycle generates 14 ticks (1766-1779)
        // Second cycle generates 14 ticks (3432-3445, but capped at 3500)
        // Plus the initial downtime tick
        // Total: 1 + 14 + 14 = 29 ticks (some may be capped)
        Assertions.assertTrue(result.ticks().size() >= 15); // At least 1 downtime + 14 first cycle
        // Verify first ticks are correct
        Assertions.assertEquals(Warper.Tick.at(100L).after(100L), result.ticks().get(0)); // downtime tick
        Assertions.assertEquals(Warper.Tick.at(1766L).after(1666L), result.ticks().get(1)); // first work cycle start
        Assertions.assertTrue(result.useDynamicResolution());
    }

    @Test
    public void testShouldUseDynamicResolutionWhenNoFinishableWorkAvailable() {
        // When getRandomFinishableWork returns null (current job can't be completed),
        // forVillager should fall back to dynamic resolution so villager can find other work.
        // This enables warp to work even when the villager's current job isn't completable.
        JobID jobID = new JobID("test", "gatherer");
        TownFlagState.Work w = new TownFlagState.Work() {
            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime,
                    long ticksElapsed
            ) {
                // No finishable work available with current job
                return null;
            }

            @Override
            public long getTotalDuration(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                throw new AssertionError("Should not be called when jobID is null");
            }

            @Override
            public int getWarpTicksPerCycle(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                throw new AssertionError("Should not be called when jobID is null");
            }
        };
        int window = 2100; // Enough for at least 2 work cycles at default 1000 tick spacing
        ImportantTicks.Config cfg = new ImportantTicks.Config(50);

        VillagerUUID vuid = VillagerUUID.random();
        ImportantTicks.Result result = ImportantTicks.forVillager(
                w, vuid, jobID, id -> false, cfg, window, 0L
        );

        // Should fall back to dynamic resolution with generated ticks
        Assertions.assertFalse(result.ticks().isEmpty(), "Should generate ticks for dynamic resolution");
        Assertions.assertTrue(result.useDynamicResolution(), "Should use dynamic resolution when current job can't be completed");
    }

    @Test
    public void testShouldReturnDowntimeTicksOnlyWhenNoFinishableWorkAfterDowntime() {
        // When villager starts in downtime but getRandomFinishableWork returns null,
        // should return just the downtime tick, not crash on getTotalDuration.
        JobID downID = new JobID("test", "downtime");
        TownFlagState.Work w = new TownFlagState.Work() {
            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID jobID,
                    Signals.DayTime dayTime,
                    long ticksElapsed
            ) {
                // No finishable work after downtime
                return null;
            }

            @Override
            public long getTotalDuration(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                throw new AssertionError("Should not be called when jobID is null");
            }

            @Override
            public int getWarpTicksPerCycle(
                    JobID jobID,
                    VillagerUUID vuid
            ) {
                throw new AssertionError("Should not be called when jobID is null");
            }
        };
        int window = 100;
        ImportantTicks.Config cfg = new ImportantTicks.Config(20);

        VillagerUUID vuid = VillagerUUID.random();
        ImportantTicks.Result result = ImportantTicks.forVillager(
                w, vuid, downID, downID::equals, cfg, window, 0L
        );

        // For downtime villagers, ticks are generated regardless of whether finishable work exists
        // (job resolution happens in PostDowntimeWarper, not during tick generation)
        // Window=100, downtime=20, remaining=80. First work cycle at baseTick=1000 is > remaining,
        // but we still generate a tick at the remaining boundary (100)
        ImmutableList<Warper.Tick> expected = ImmutableList.of(
                Warper.Tick.at(20L).after(20L),  // downtime tick
                Warper.Tick.at(100L).after(80L)  // work tick at window boundary
        );
        Assertions.assertEquals(expected, result.ticks());
        Assertions.assertTrue(result.useDynamicResolution());
    }

}