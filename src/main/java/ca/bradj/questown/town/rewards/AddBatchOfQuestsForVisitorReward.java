package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AddBatchOfQuestsForVisitorReward extends MCReward {

    public static final String ID = "add_random_batch_of_quests";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private final TownInterface town;
    private @Nullable VillagerUUID visitorUUID;

    public AddBatchOfQuestsForVisitorReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            @Nullable UUID visitorUUID
    ) {
        super(rType);
        this.visitorUUID = VillagerUUID.from(visitorUUID);
        this.town = entity;
    }

    public AddBatchOfQuestsForVisitorReward(
            @NotNull TownInterface entity,
            @Nullable UUID visitorUUID
    ) {
        this(RewardsInit.RANDOM_BATCH_FOR_VILLAGER.get(), entity, visitorUUID);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.getQuestHandle().addBatchOfQuestsForVisitor(visitorUUID);
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return true;
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag compoundTag = new CompoundTag();
        if (this.visitorUUID != null) {
            visitorUUID.writeToNBT(compoundTag, NBT_VISITOR_UUID);
        }
        return compoundTag;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (tag.contains(NBT_VISITOR_UUID)) {
            this.visitorUUID = VillagerUUID.fromNBT(tag, NBT_VISITOR_UUID);
        }
    }

    @Override
    public String toString() {
        return "AddBatchOfRandomQuestsForVisitorReward{" +
                "town=" + town.getUUID() +
                ", visitorUUID=" + visitorUUID +
                '}';
    }

    @Override
    public String toNiceString() {
        String vid = visitorUUID == null ? "unowned" : visitorUUID.toString();
        return "AddRandomQuestBatch[" + UtilClean.truncateMiddle(vid) + "]";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return RewardsInit.RANDOM_BATCH_FOR_VILLAGER.get().equals(reward);
    }
}
