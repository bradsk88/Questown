package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.BOPDepositorWork;

/**
 * Exits for a townie the job board has nothing for. Pinned by {@code BoardIdleExitsTest}.
 */
public final class BoardIdleExits {

    private BoardIdleExits() {
    }

    /**
     * The townie is still on its BOP-deposit job when the board has nothing else for it. The
     * board's BOP branch (latch set) comes first, so reaching this means the deposit is done:
     * the job's only "tool" is a BOP, so staying put showed "needs Block of Progress" for minutes
     * (r17 #17). The caller sends it to downtime.
     */
    public static boolean depositFinished(JobID current) {
        return BOPDepositorWork.ID.equals(current.jobId());
    }
}
