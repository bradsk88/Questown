package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rewards.RewardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

public class MCDelayedReward extends MCReward {

    public static final String ID = "delayed";
    private static final String NBT_CHILD = "child";
    private static final String NBT_TIME_OF_DAY = "time_of_day";
    private static final String NBT_REWARDED_AT_TIME_OF_DAY = "rewarded_at_time_of_day";
    private final TownInterface town;

    private MCReward child;
    private long rewardedAtTimeOfDay;
    private long timeOfDay;

    public MCDelayedReward(
            TownInterface town,
            MCReward child,
            long timeOfDay,
            long rewardedAtTimeOfDay
    ) {
        super(RewardsInit.DELAYED.get());
        this.town = town;
        this.child = child;
        this.timeOfDay = timeOfDay;
        this.rewardedAtTimeOfDay = rewardedAtTimeOfDay;
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.addTimedReward(this);
    }

    public MCDelayedReward(
            RewardType<? extends MCReward> rType,
            TownInterface town
    ) {
        super(rType);
        this.town = town;
    }

    public boolean tryClaim(float timeOfDay) {
        if (timeOfDay >= this.rewardedAtTimeOfDay) {
            return false;
        }
        if (timeOfDay < this.timeOfDay) {
            return false;
        }
        if (this.child.isApplied()) {
            return false;
        }
        this.child.claim();
        return true;
    }

    protected Tag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        tag.put(NBT_CHILD, MCReward.SERIALIZER.serializeNBT(child));
        tag.putLong(NBT_TIME_OF_DAY, timeOfDay);
        tag.putLong(NBT_REWARDED_AT_TIME_OF_DAY, rewardedAtTimeOfDay);
        return tag;
    }

    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (this.child != null) {
            throw new IllegalStateException("Already initialized");
        }
        this.child = MCReward.SERIALIZER.deserializeNBT(entity, tag.getCompound(NBT_CHILD));
        this.timeOfDay = tag.getLong(NBT_TIME_OF_DAY);
        this.rewardedAtTimeOfDay = tag.getLong(NBT_REWARDED_AT_TIME_OF_DAY);
    }
}
