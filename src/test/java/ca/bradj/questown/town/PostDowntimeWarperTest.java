package ca.bradj.questown.town;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.town.entity.TownFlagState;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class PostDowntimeWarperTest {

    /**
     * When a villager has collected supplies for a job and that job is still viable
     * (supplies available, job block ready), the warper should NOT switch to a different job.
     */
    @Test
    public void whenVillagerHasSuppliesForJob_andJobIsViable_shouldNotSwitchJobs() {
        JobID stickJob = new JobID("crafter", "stick");
        JobID bowlJob = new JobID("crafter", "bowl");

        List<JobID> resolvedJobs = new ArrayList<>();

        TownFlagState.Work work = new TownFlagState.Work() {
            private int callCount = 0;

            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID currentJob,
                    Signals.DayTime dayTime,
                    long ticksElapsed
            ) {
                // Alternate between jobs to simulate random selection
                JobID result = (callCount++ % 2 == 0) ? stickJob : bowlJob;
                resolvedJobs.add(result);
                return result;
            }

            @Override
            public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
                return 100;
            }

            @Override
            public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
                return 5;
            }
        };

        PostDowntimeWarper warper = new PostDowntimeWarper(
                work,
                stickJob,
                0,
                BlockPos.ZERO
        );

        // Simulate multiple warp ticks by calling resolveJob multiple times
        // In the real system, warp() calls getRandomFinishableWork on each tick
        JobID job1 = warper.resolveJob(100);
        JobID job2 = warper.resolveJob(100);
        JobID job3 = warper.resolveJob(100);

        // Expected: Once a job is selected, it should stay consistent within a cycle
        // (i.e., all three should be the same job)
        Assertions.assertEquals(job1, job2, "Job should not switch mid-cycle");
        Assertions.assertEquals(job2, job3, "Job should not switch mid-cycle");
    }

    /**
     * When villager inventory is empty (cycle complete), job should be allowed to change.
     */
    @Test
    public void whenVillagerInventoryIsEmpty_shouldAllowJobSwitch() {
        JobID stickJob = new JobID("crafter", "stick");
        JobID bowlJob = new JobID("crafter", "bowl");

        TownFlagState.Work work = new TownFlagState.Work() {
            private int callCount = 0;

            @Override
            public void recomputeNow() {
            }

            @Override
            public @Nullable JobID getRandomFinishableWork(
                    JobID currentJob,
                    Signals.DayTime dayTime,
                    long ticksElapsed
            ) {
                // Alternate between jobs
                return (callCount++ % 2 == 0) ? stickJob : bowlJob;
            }

            @Override
            public long getTotalDuration(JobID jobID, VillagerUUID vuid) {
                return 100;
            }

            @Override
            public int getWarpTicksPerCycle(JobID jobID, VillagerUUID vuid) {
                return 5;
            }
        };

        PostDowntimeWarper warper = new PostDowntimeWarper(
                work,
                stickJob,
                0,
                BlockPos.ZERO
        );

        // First cycle: villager has no items, picks a job
        JobID job1 = warper.resolveJob(100, false);

        // Mid-cycle: villager has items, should stick with same job
        JobID job2 = warper.resolveJob(100, true);
        Assertions.assertEquals(job1, job2, "Should not switch while villager has items");

        // Cycle complete: villager inventory empty, should allow new job
        JobID job3 = warper.resolveJob(100, false);

        // job3 could be same or different - the point is getRandomFinishableWork was called again
        // Since our mock alternates, job3 should be different from job1
        Assertions.assertNotEquals(job1, job3, "Should allow job switch when inventory is empty");
    }
}
