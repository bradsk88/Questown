package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Posts the day-2 "post your first work request" quest (F2:
 * .scratch/day1-playtest-2026-10-11/job-board-organic-introduction). Applied when the
 * basics batch (job board + store room) completes - by then the board exists and a
 * villager is present, so the quest's ask is doable.
 */
public class PostFirstWorkRequestQuestReward extends MCReward {

    public static final String ID = "post_first_work_request_quest";

    private final TownInterface town;

    public PostFirstWorkRequestQuestReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity
    ) {
        super(rType);
        this.town = entity;
    }

    public PostFirstWorkRequestQuestReward(
            @NotNull TownInterface entity
    ) {
        this(RewardsInit.FIRST_WORK_REQUEST.get(), entity);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.getQuestHandle().addFirstWorkRequestBatch();
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
        return "PostFirstWorkRequestQuestReward{town=" + town + '}';
    }

    @Override
    public String toNiceString() {
        return "PostFirstWorkRequestQuest";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return Objects.equals(RewardsInit.FIRST_WORK_REQUEST.get().id, reward.id);
    }
}
