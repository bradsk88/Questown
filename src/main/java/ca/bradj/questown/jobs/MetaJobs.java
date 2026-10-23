package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.BOPDepositorWork;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;

/**
 * Upkeep jobs every root carries (eating, downtime, rest, seeking work, BOP deposit) as opposed to
 * the root's own work. Reads only the jobs' compile-time ID constants, so it loads in a unit test.
 */
public final class MetaJobs {

    private MetaJobs() {
    }

    public static boolean isMeta(JobID job) {
        if (DiningStages.isDiner(job)) {
            return true;
        }
        return switch (job.jobId()) {
            case DowntimeWork.ID, ResterWork.ID, WorkSeekerJob.WORK_ID, BOPDepositorWork.ID -> true;
            default -> false;
        };
    }
}
