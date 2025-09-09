package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rewards.Registry;
import ca.bradj.questown.town.rewards.RewardType;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public abstract class MCReward extends Reward {

    public static final Serializer SERIALIZER = new Serializer();
    public final RewardType<? extends MCReward> rType;

    public MCReward(
            RewardType<? extends MCReward> rType
    ) {
        super();
        this.rType = rType;
    }

    @Override
    protected String getName() {
        return rType.id.toString();
    }

    public abstract String toNiceString();

    public abstract boolean contains(@NotNull RewardType<?> reward);

    public static class Serializer {

        private static final String NBT_REWARD_TYPE = "reward_type";
        private static final String NBT_REWARD_DATA = "reward_data";
        private static final String NBT_REWARD_APPLIED = "reward_applied";

        public MCReward deserializeNBT(
                TownInterface entity,
                CompoundTag tag
        ) {
            ResourceLocation rewardType = new ResourceLocation(tag.getString(NBT_REWARD_TYPE));
            RewardType<? extends MCReward> rType = Registry.REWARD_TYPES.getValue(rewardType);
            if (rType == null) {
                rType = RewardsInit.VISITOR.get();
            }
            MCReward r = rType.create(rType, entity);
            r.deserializeNbt(entity, tag.getCompound(NBT_REWARD_DATA));
            if (tag.contains(NBT_REWARD_APPLIED) && tag.getBoolean(NBT_REWARD_APPLIED)) {
                r.markApplied();
            }
            return r;
        }

        public Tag serializeNBT(MCReward reward) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_REWARD_TYPE, reward.rType.id.toString());
            tag.put(NBT_REWARD_DATA, reward.serializeNbt());
            tag.putBoolean(NBT_REWARD_APPLIED, reward.isApplied());
            return tag;
        }
    }

    protected abstract CompoundTag serializeNbt();
    protected abstract void deserializeNbt(TownInterface entity, CompoundTag tag);
}
