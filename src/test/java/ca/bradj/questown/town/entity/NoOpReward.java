package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.rewards.RewardType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

class NoOpReward extends MCReward {

    static final RewardType<NoOpReward> TYPE = new RewardType<>(
            (rType, entity) -> new NoOpReward(),
            new ResourceLocation("questown", "test_noop")
    );

    NoOpReward() {
        super(TYPE);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> {};
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return false;
    }

    @Override
    protected CompoundTag serializeNbt() {
        return new CompoundTag();
    }

    @Override
    protected void deserializeNbt(TownInterface entity, CompoundTag tag) {
    }

    @Override
    public String toNiceString() {
        return "NoOp";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return false;
    }
}
