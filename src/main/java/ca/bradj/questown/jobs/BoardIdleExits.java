package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.BOPDepositorWork;

/** Where a villager goes when the job board has nothing for it. */
public final class BoardIdleExits {

    private BoardIdleExits() {
    }

    /** True when the villager is still on the Block of Progress deposit job, which means the deposit is done. */
    public static boolean depositFinished(JobID current) {
        return BOPDepositorWork.ID.equals(current.jobId());
    }
}
