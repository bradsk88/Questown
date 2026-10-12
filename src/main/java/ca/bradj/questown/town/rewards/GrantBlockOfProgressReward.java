package ca.bradj.questown.town.rewards;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Grants Blocks of Progress straight to the flag's BOP store - the small, concrete
 * reward the onboarding quest batches hand out (e.g. the day-2 "first work request"
 * quest). Mirrors the tutorial's BOP grant (TownFlagTutorialAdapter.grantBop).
 */
public class GrantBlockOfProgressReward extends MCReward {

    public static final String ID = "grant_block_of_progress";

    private static final String NBT_AMOUNT = "amount";

    private final TownInterface town;
    private int amount;

    public GrantBlockOfProgressReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            int amount
    ) {
        super(rType);
        this.town = entity;
        this.amount = amount;
    }

    public GrantBlockOfProgressReward(
            @NotNull TownInterface entity,
            int amount
    ) {
        this(RewardsInit.GRANT_BOP.get(), entity, amount);
    }

    /** Deserialization entry: amount is restored from NBT via {@link #deserializeNbt}. */
    public static GrantBlockOfProgressReward emptyForDeserializing(
            RewardType<? extends MCReward> rewardType,
            TownInterface townInterface
    ) {
        return new GrantBlockOfProgressReward((RewardType<GrantBlockOfProgressReward>) rewardType, townInterface, 0);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> {
            TownFlagBlockEntity flag = (TownFlagBlockEntity) town;
            flag.grantBlockOfProgress(amount);
            QT.QUESTS_LOGGER.info("Granted {} Block(s) of Progress to the flag", amount);
        };
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag t = new CompoundTag();
        t.putInt(NBT_AMOUNT, amount);
        return t;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        this.amount = tag.getInt(NBT_AMOUNT);
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return false;
    }

    @Override
    public String toString() {
        return "GrantBlockOfProgressReward{town=" + town + ", amount=" + amount + '}';
    }

    @Override
    public String toNiceString() {
        return "GrantBlockOfProgress[" + amount + "]";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return Objects.equals(RewardsInit.GRANT_BOP.get().id, reward.id);
    }
}
