package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rewards.Registry;
import ca.bradj.questown.town.rewards.RewardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public abstract class MCReward extends Reward {

    public static final Serializer SERIALIZER = new Serializer();
    private final RewardType<? extends MCReward> rType;

    public MCReward(
            RewardType<? extends MCReward> rType,
            RewardApplier applier
    ) {
        super(applier);
        this.rType = rType;
    }

    @Override
    protected String getName() {
        return rType.getRegistryName().toString();
    }

    public static class Serializer {

        private static final String NBT_REWARD_TYPE = "reward_type";
        private static final String NBT_REWARD_DATA = "reward_data";

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
            return r;
        }

        public Tag serializeNBT(MCReward reward) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_REWARD_TYPE, reward.rType.getRegistryName().toString());
            tag.put(NBT_REWARD_DATA, reward.serializeNbt());
            return tag;
        }
    }

    protected abstract Tag serializeNbt();
    protected abstract void deserializeNbt(TownInterface entity, CompoundTag tag);
}
