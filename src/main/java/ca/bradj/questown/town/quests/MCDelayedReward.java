package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rewards.RewardType;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class MCDelayedReward extends MCReward implements MCRewardContainer {

    public static final long START_OF_DAY = 1;

    public static final String ID = "delayed";
    private static final String NBT_CHILD = "child";
    private final TownInterface town;

    private MCReward child;

    public MCDelayedReward(
            TownInterface town,
            MCReward child
    ) {
        super(RewardsInit.DELAYED.get());
        this.town = town;
        this.child = child;
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.addMorningReward(this.child);
    }

    public MCDelayedReward(
            RewardType<? extends MCReward> rType,
            TownInterface town
    ) {
        super(rType);
        this.town = town;
    }

    public Tag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        tag.put(NBT_CHILD, MCReward.SERIALIZER.serializeNBT(child));
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
    }

    @Override
    public Collection<MCReward> getContainedRewards() {
        return ImmutableList.of(child);
    }

    @Override
    public String toString() {
        return "MCDelayedReward{" +
                "child=" + child +
                '}';
    }
}
