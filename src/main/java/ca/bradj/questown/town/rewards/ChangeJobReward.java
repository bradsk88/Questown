package ca.bradj.questown.town.rewards;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
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
    private static final String NBT_ONBOARDING = "onboarding";
    private final TownInterface town;
    private UUID visitorUUID;
    private String jobName;
    // The onboarding-flow cook (tutorial job-change-and-food phase) gets one starter
    // recipe on top of the root's universal starter set. Gated so a plain (non-onboarding)
    // job change — the shared generic path — does NOT unlock recipe jobs.
    private boolean onboarding = false;

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

    /**
     * Marks this job change as the onboarding-flow one (the tutorial cook). The
     * onboarding cook additionally unlocks a starter recipe job on top of the
     * universal starter set; a non-onboarding job change must not.
     */
    public ChangeJobReward onboarding(boolean onboarding) {
        this.onboarding = onboarding;
        return this;
    }

    public boolean isOnboarding() {
        return onboarding;
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
            // Onboarding cook only: the universal starter set (extract + fuel) opens a
            // furnace but cannot cook anything — add the onboarding starter recipe so
            // the town can turn raw food into a meal. A plain job change leaves recipe
            // jobs locked on the level tree (the food-cooking progression stays gated).
            if (onboarding && "cook".equals(jobName)) {
                JobID starter = new JobID("cook", "mutton");
                town.getVillagerHandle().unlockJob(visitorUUID, starter);
                QT.JOB_LOGGER.info(
                        "Onboarding cook {} granted starter recipe {} on top of the universal starter set",
                        visitorUUID,
                        starter
                );
            }
            defaultWork.forEach(v -> {
                ItemStack output = ServerJobsRegistry.getDefaultWorkForNewWorker(town.getServerLevel(), v);
                if (output.isEmpty()) {
                    return;
                }
                town.getWorkHandle().requestWork(output.getItem());
                QT.JOB_LOGGER.debug("Request was added to job board automatically");
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
        compoundTag.putBoolean(NBT_ONBOARDING, this.onboarding);
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
        this.onboarding = tag.getBoolean(NBT_ONBOARDING);
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
