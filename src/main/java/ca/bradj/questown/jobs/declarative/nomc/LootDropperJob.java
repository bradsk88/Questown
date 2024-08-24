package ca.bradj.questown.jobs.declarative.nomc;

import ca.bradj.questown.jobs.JobID;

public class LootDropperJob {
    public static final String WORK_ID = "dropping_loot";

    public static JobID getIDForRoot(JobID j) {
        return newIDForRoot(j.rootId());
    }

    public static JobID newIDForRoot(String jobName) {
        return new JobID(jobName, WORK_ID);
    }

    public static boolean isDropping(JobID jobName) {
        return WORK_ID.equals(jobName.jobId());
    }
}
