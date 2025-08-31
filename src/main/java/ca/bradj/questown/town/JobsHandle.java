package ca.bradj.questown.town;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;

public interface JobsHandle {
    boolean changeFromBoard(
            VillagerUUID ownerUUID,
            JobID currentJob
    );

    void change(
            VillagerUUID visitorUUID,
            JobID jobID,
            boolean announce
    );
}
