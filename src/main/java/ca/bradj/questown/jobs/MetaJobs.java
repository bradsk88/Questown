package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.BOPDepositorWork;
import ca.bradj.questown.jobs.declarative.DowntimeWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;

/** Upkeep jobs every root has (eating, downtime, rest, seeking work, Block of Progress deposit), as opposed to its own work. */
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
