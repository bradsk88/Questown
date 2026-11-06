package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;
import java.util.UUID;

public interface Delegator<ENTITY> {
    JobID getJobId(ENTITY vEntity);

    UUID getUUID(ENTITY v);

    void setJob(
            UUID visitorUUID,
            JobID jobName,
            ENTITY entity,
            boolean announce
    );

    void setChanged();

    VillagerSleepModule<ENTITY> getSleepModule();

    void debug(
            QT.QTLogger villagerLogger,
            String category,
            String s,
            Object... args
    );

    boolean isUUID(
            ENTITY v,
            UUID ownerUUID
    );

    void invalidateCachedWorkPossibilities();

    /** Drops the villager's job-block states, on a job change, so the new job doesn't read the old job's. */
    void resetWorkStatus(UUID villagerUUID);

    HealingModule<ENTITY> getHealingModule();

    void addChangeListener(
            ENTITY vEntity,
            Runnable runnable
    );

    void broadcastMessage(
            String s,
            Object... args
    );

    ImmutableList<JobID> shuffle(ImmutableSet<JobID> jobIDS);

    Optional<JobID> getOvernightJobOverride();

    void discardEntity(ENTITY visitorMobEntity);

    boolean isRemoved(ENTITY e);

    boolean isDining(ENTITY v);

    boolean canStopWorkingAtAnyTime(ENTITY v);

    void freeze(
            ENTITY v,
            int ticks
    );

    float getMood(UUID uuid);

    void unlockJob(
            ENTITY v,
            JobID newJob
    );

    /** The jobs a villager starts with in this root; a job change unlocks them all. */
    ImmutableList<JobID> getDefaultWork(JobID root);

    /** Posts the root's starter requests on the job board, so a new worker has something to do. */
    void postStarterRequests(JobID root);

    ImmutableMap<UUID, ImmutableSet<JobID>> getUnlockedJobs();

    ImmutableSet<JobID> getChildJobsKnownToExist(JobID jobId);
}
