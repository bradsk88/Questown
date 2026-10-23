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

    /**
     * Drop the villager's per-owner job-block states. Called on a job ROOT change: the old
     * job's leftover states would otherwise be read by the new job (2026-10-17 playtest: the
     * gatherer left the gate mat at its terminal state 3, the new hunter - also maxState 3 -
     * read it as its own finished hunt and parked in COLLECTING_SUPPLIES).
     */
    void resetWorkStatus(UUID villagerUUID);

    HealingModule<ENTITY> getHealingModule();

    void broadcastMessage(
            String s,
            Object... args
    );

    ImmutableList<JobID> shuffle(ImmutableSet<JobID> jobIDS);

    Optional<JobID> getOvernightJobOverride();

    void discardEntity(ENTITY visitorMobEntity);

    /** True once the entity has left the world (unloaded with its chunk, or discarded). */
    boolean isRemoved(ENTITY e);

    boolean isDining(ENTITY v);

    boolean canStopWorkingAtAnyTime(ENTITY v);

    void freeze(
            ENTITY v,
            int ticks
    );

    float getMood(UUID uuid);

    /** The ids of the townie's active mood effects (for the stats page). */
    default ImmutableList<String> getMoodEffectIds(UUID uuid) {
        return ImmutableList.of();
    }

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

    /**
     * The jobs a villager starts with when it takes this root (a cook: extract + fuel + the
     * starter recipe). A root change must unlock all of them, not only the job it switches to.
     */
    ImmutableList<JobID> getDefaultWork(JobID root);

    /**
     * Post the root's starter requests on the job board (each default work's initial request), as
     * a job-change quest reward does. Without them every work of the new root reads "Product not
     * requested" and the villager idles.
     */
    void postStarterRequests(JobID root);

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
