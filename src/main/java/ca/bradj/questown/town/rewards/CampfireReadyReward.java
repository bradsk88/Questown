package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * The day-one campfire-quest reward: the "your camp is ready" tutorial toast.
 * The {@code first_campfire_sleep} advancement already fires when the player
 * actually sleeps on the campfire that night, so this reward carries no
 * advancement of its own (.scratch/mat-gated-arrival, decision 2: the reward
 * communicates "this campfire is your camp" via the toast).
 */
public class CampfireReadyReward extends MCReward {

    public static final String ID = "campfire_ready";

    public static final String TOAST_TITLE_KEY = "tutorial.campfire_ready.title";
    public static final String TOAST_DESCRIPTION_KEY = "tutorial.campfire_ready.description";

    private final TownInterface town;

    public CampfireReadyReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity
    ) {
        super(rType);
        this.town = entity;
    }

    public CampfireReadyReward(
            @NotNull TownInterface entity
    ) {
        this(RewardsInit.CAMPFIRE_READY.get(), entity);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> ((TownFlagBlockEntity) town).broadcastTutorialToast(TOAST_TITLE_KEY, TOAST_DESCRIPTION_KEY);
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
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
    }

    @Override
    public String toString() {
        return "CampfireReadyReward{town=" + town + '}';
    }

    @Override
    public String toNiceString() {
        return "CampfireReadyToast";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return Objects.equals(RewardsInit.CAMPFIRE_READY.get().id, reward.id);
    }
}
