package ca.bradj.questown.town.rewards;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
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
            UUID visitorUUID, // Allowed to be null because rewards get deserialized at runtime.
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
            town.changeJobForVisitor(visitorUUID, jobID);
            ImmutableList<JobID> defaultWork = JobsRegistry.getDefaultWork(jobID);
            defaultWork.forEach(v -> {
                ItemStack output = JobsRegistry.getOutput(v);
                if (output.isEmpty()) {
                    return;
                }
                ImmutableList<Ingredient> result = ImmutableList.of(Ingredient.of(output));
                town.requestResult(result);
                QT.JOB_LOGGER.debug("Request was added to job board automatically");
            });
        };
    }

    @Override
    protected Tag serializeNbt() {
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
}
