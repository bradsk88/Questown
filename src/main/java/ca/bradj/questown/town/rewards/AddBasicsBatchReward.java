package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Posts the day-one "basics" quest batch (job board + store room) to the
 * quests UI. Applied when the welcome-mat gate quest completes
 * (.scratch/mat-gated-arrival, decision 3): the mat's completion hands the
 * player the initial-room quests that fill the evening until the first
 * villager arrives in the morning.
 */
public class AddBasicsBatchReward extends MCReward {

    public static final String ID = "add_basics_batch";

    private final TownInterface town;

    public AddBasicsBatchReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity
    ) {
        super(rType);
        this.town = entity;
    }

    public AddBasicsBatchReward(
            @NotNull TownInterface entity
    ) {
        this(RewardsInit.BASICS_BATCH.get(), entity);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.getQuestHandle().addBasicsBatch();
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return true;
    }

    @Override
    protected CompoundTag serializeNbt() {
        return new CompoundTag();
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
    }

    @Override
    public String toString() {
        return "AddBasicsBatchReward{town=" + town + '}';
    }

    @Override
    public String toNiceString() {
        return "AddBasicsBatch[job_board+store_room]";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return Objects.equals(RewardsInit.BASICS_BATCH.get().id, reward.id);
    }
}
