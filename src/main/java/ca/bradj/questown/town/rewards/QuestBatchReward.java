package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

public class QuestBatchReward extends MCReward {

    private final TownInterface town;

    private MCQuestBatch batch;

    public QuestBatchReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            MCQuestBatch batch
    ) {
        super(rType);
        this.batch = batch;
        this.town = entity;
    }

    public QuestBatchReward(
            @NotNull TownInterface entity,
            @NotNull MCQuestBatch batch
    ) {
        this(RewardsInit.RANDOM_BATCH_FOR_VILLAGER.get(), entity, batch);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.addBatchOfQuests(batch);
    }

    @Override
    protected Tag serializeNbt() {
        return MCQuestBatch.SERIALIZER.serializeNBT(this.batch);
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (this.batch != null) {
            throw new IllegalStateException("Already initialized");
        }
        this.batch = MCQuestBatch.SERIALIZER.deserializeNBT(entity, tag);
    }
}
