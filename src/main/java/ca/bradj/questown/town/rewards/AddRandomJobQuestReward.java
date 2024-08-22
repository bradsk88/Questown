package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AddRandomJobQuestReward extends MCReward {

    public static final String ID = "add_random_job_quest";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private final TownInterface town;
    private UUID visitorUUID;

    private AddRandomJobQuestReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            UUID visitorUUID // Allowed to be null because rewards get deserialized at runtime.
    ) {
        super(rType);
        this.visitorUUID = visitorUUID;
        this.town = entity;
    }

    public AddRandomJobQuestReward(
            @NotNull TownInterface entity,
            @NotNull UUID visitorUUID
    ) {
        this(RewardsInit.RANDOM_JOB_FOR_VILLAGER.get(), entity, visitorUUID);
    }

    public AddRandomJobQuestReward(RewardType<? extends MCReward> rType, TownInterface flag, RewardsInit reg) {
        this(rType, flag, (UUID) null);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.addRandomJobQuestForVisitor(visitorUUID);
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putUUID(NBT_VISITOR_UUID, this.visitorUUID);
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
    }
}
