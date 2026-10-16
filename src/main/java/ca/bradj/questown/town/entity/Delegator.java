package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;

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

    boolean isDining(ENTITY v);

    boolean canStopWorkingAtAnyTime(ENTITY v);

    void freeze(
            ENTITY v,
            int ticks
    );

    float getMood(UUID uuid);

    /** Apply a mood effect to the townie, expiring at the given game tick. */
    void applyMoodEffect(
            UUID uuid,
            ResourceLocation effect,
            long untilTick
    );

    void unlockJob(
            ENTITY v,
            JobID newJob
    );

    ImmutableMap<UUID, ImmutableSet<JobID>> getUnlockedJobs();

    ImmutableSet<JobID> getChildJobsKnownToExist(JobID jobId);

    /**
     * The user-facing display name ("Villager N") for this townie, empty when
     * none is assigned - callers fall back to a truncated UUID. Towns override
     * with the display-number assignment; non-town delegators get "".
     */
    default String displayNameFor(UUID vuid) {
        return "";
    }
}
