package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.rewards.Registry;
import ca.bradj.questown.town.rewards.RewardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public class Reward {

    public static final Serializer SERIALIZER = new Serializer();

    private final RewardApplier applier;
    private final RewardType<? extends Reward> rType;
    private boolean applied;

    public interface RewardApplier {
        void apply();
    }

    public Reward(RewardType<? extends Reward> rType, RewardApplier applier) {
        this.applier = applier;
        this.rType = rType;
    }

    void claim() {
        this.applier.apply();
        this.applied = true;
    }

    public static class Serializer {

        private static final String NBT_REWARD_TYPE = "reward_type";

        public Reward deserializeNBT(TownFlagBlockEntity entity, CompoundTag tag) {
            ResourceLocation rewardType = new ResourceLocation(tag.getString(NBT_REWARD_TYPE));
            RewardType<? extends Reward> rType = Registry.REWARD_TYPES.getValue(rewardType);
            if (rType == null) {
                rType = RewardsInit.VISITOR.get();
            }
            return rType.create(rType, entity);
        }

        public Tag serializeNBT(Reward reward) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_REWARD_TYPE, reward.rType.getRegistryName().toString());
            return tag;
        }
    }

}
