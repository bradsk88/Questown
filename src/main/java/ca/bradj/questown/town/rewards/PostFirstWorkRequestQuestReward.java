package ca.bradj.questown.town.rewards;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Posts day 2's first-work-request quest. With no villager yet, it waits for the morning. */
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
        return () -> {
            if (!((TownFlagBlockEntity) town).hasVillagers()) {
                QT.QUESTS_LOGGER.info("First-work-request quest deferred to the next morning: no villager yet");
                town.addMorningReward(new PostFirstWorkRequestQuestReward(town));
                return;
            }
            town.getQuestHandle().addFirstWorkRequestBatch();
        };
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
