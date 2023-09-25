package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
        return () -> town.changeJobForVisitor(visitorUUID, jobName);
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
}
