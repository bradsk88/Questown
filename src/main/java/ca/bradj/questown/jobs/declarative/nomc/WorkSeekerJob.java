package ca.bradj.questown.jobs.declarative.nomc;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.jobs.JobID;

public class WorkSeekerJob {
    public static final String WORK_ID = "seeking_work";
    public static final int BLOCK_STATE_NO_JOBS = 0;
    public static final int BLOCK_STATE_JOBS_AVAIlABLE = 1;
    public static final int MAX_STATE = BLOCK_STATE_JOBS_AVAIlABLE;

    public static JobID getIDForRoot(JobID j) {
        if (Config.JOB_BOARD_ENABLED.get()) {
            return newIDForRoot(j.rootId());
        }
        return j;
    }

    public static boolean isSeekingWork(JobID s) {
        return WORK_ID.equals(s.jobId());
    }

    public static JobID newIDForRoot(String jobName) {
        return new JobID(jobName, WORK_ID);
    }

}
