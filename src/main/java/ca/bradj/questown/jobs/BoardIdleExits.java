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

    /**
     * The board found no work, and at least one job was turned down only because it would not
     * finish before the next day window ({@code ServerJobsRegistry.canFit}). The townie relaxes
     * until morning instead of idling in seeking_work, whose status read as missing supplies
     * (r17 #10, dev 2026-10-25: "or just skip ahead to 'relaxing'").
     */
    public static boolean tooLateInTheDay(boolean foundWork, int rejectedForTime) {
        return !foundWork && rejectedForTime > 0;
    }
}
