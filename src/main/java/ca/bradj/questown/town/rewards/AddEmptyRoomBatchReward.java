package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Posts the day-one "empty room" quest batch (D#2). Applied when the welcome-mat
 * gate quest completes: the player learns "a room with a registered door" before
 * the basics batch (job board + store room) is posted, which happens when this
 * batch's quest completes.
 */
public class AddEmptyRoomBatchReward extends MCReward {

    public static final String ID = "add_empty_room_batch";

    private final TownInterface town;

    public AddEmptyRoomBatchReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity
    ) {
        super(rType);
        this.town = entity;
    }

    public AddEmptyRoomBatchReward(
            @NotNull TownInterface entity
    ) {
        this(RewardsInit.EMPTY_ROOM_BATCH.get(), entity);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.getQuestHandle().addEmptyRoomBatch();
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
        return "AddEmptyRoomBatchReward{town=" + town + '}';
    }

    @Override
    public String toNiceString() {
        return "AddEmptyRoomBatch";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return Objects.equals(RewardsInit.EMPTY_ROOM_BATCH.get().id, reward.id);
    }
}
