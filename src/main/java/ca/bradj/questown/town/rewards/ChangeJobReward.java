package ca.bradj.questown.town.rewards;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ChangeJobReward extends MCReward {

    public static final String ID = "change_job";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private static final String NBT_JOB_NAME = "job_name";
    private final TownInterface town;
    private UUID visitorUUID;
    private String jobName;

    public ChangeJobReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            UUID visitorUUID,
            // Allowed to be null because rewards get deserialized at runtime.
            String jobName
    ) {
        super(rType);
        this.visitorUUID = visitorUUID;
        this.town = entity;
        this.jobName = jobName;
    }

    public ChangeJobReward(
            @NotNull TownInterface entity,
            @NotNull UUID visitorUUID,
            @NotNull String jobName
    ) {
        this(RewardsInit.CHANGE_JOB.get(), entity, visitorUUID, jobName);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> {
            JobID jobID = WorkSeekerJob.newIDForRoot(jobName);
            town.getVillagerHandle().changeJobForVillager(visitorUUID, jobID, false);
            ImmutableList<JobID> defaultWork = ServerJobsRegistry.getDefaultWork(jobID);
            // Registering a townie unlocks the specific works for its current job
            // (TownVillagerHandle.register). A job *change* after registration does not
            // re-run that, so the new root's works stay locked: the work-seeker finds an
            // empty set, logs "No jobs found for root ID", and the villager soft-locks in
            // seeking_work. Unlock the specific works (not the root meta-ID, which
            // isUnlocked never matches) the same way register does.
            for (JobID workID : defaultWork) {
                town.getVillagerHandle().unlockJob(visitorUUID, workID);
            }
            // Every cook gets the starter recipe (cook/mutton) through the
            // default-work unlock loop above: it is listed in the
            // simple-furnace template's `starter_recipes`, so it is part of the
            // root's initial set for all cooks (onboarding or not). The
            // 2026-10-12 day-1 playtest ruled a manually-created cook with only
            // extract + fuel wrong ("had no cook jobs"); the remaining recipes
            // stay progression-gated.
            defaultWork.forEach(v -> {
                ItemStack output = ServerJobsRegistry.getDefaultWorkForNewWorker(town.getServerLevel(), v);
                if (output.isEmpty()) {
                    return;
                }
                // The board's "auto-request" toggle (F2, default ON): when the player has
                // switched it off, a new root's initial requests are NOT auto-posted -
                // posting is left to them. Manual requests are never gated.
                if (((TownFlagBlockEntity) town).isBoardAutoRequest()) {
                    town.getWorkHandle().requestWork(output.getItem());
                    QT.JOB_LOGGER.debug("Request was added to job board automatically");
                }
            });
        };
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return false;
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putUUID(NBT_VISITOR_UUID, this.visitorUUID);
        compoundTag.putString(NBT_JOB_NAME, this.jobName);
        return compoundTag;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (tag.contains(NBT_VISITOR_UUID)) {
            this.visitorUUID = tag.getUUID(NBT_VISITOR_UUID);
        }
        if (tag.contains(NBT_JOB_NAME)) {
            this.jobName = tag.getString(NBT_JOB_NAME);
        }
    }

    public String getJobName() {
        return jobName;
    }

    @Override
    public String toNiceString() {
        return "ChangeJob:" + jobName;
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return rType.equals(reward);
    }
}
