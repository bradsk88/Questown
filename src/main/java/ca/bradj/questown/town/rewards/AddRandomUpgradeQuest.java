package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AddRandomUpgradeQuest extends MCReward {

    public static final String ID = "add_random_upgrade_quest";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private final TownInterface town;
    private VillagerUUID visitorUUID;

    private AddRandomUpgradeQuest(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            // Allowed to be null because rewards get deserialized at runtime.
            // But should never be /set/ to null.
            VillagerUUID visitorUUID
    ) {
        super(rType);
        this.visitorUUID = visitorUUID;
        this.town = entity;
    }

    /**
     * @deprecated Use constructor which takes VillagerUUID type.
     */
    @Deprecated(forRemoval = true)
    public AddRandomUpgradeQuest(
            @NotNull TownInterface entity,
            @NotNull UUID visitorUUID
    ) {
        this(RewardsInit.RANDOM_UPGRADE_FOR_VILLAGER.get(), entity, VillagerUUID.from(visitorUUID));
    }

    public AddRandomUpgradeQuest(
            @NotNull TownInterface entity,
            @NotNull VillagerUUID visitorUUID
    ) {
        this(RewardsInit.RANDOM_UPGRADE_FOR_VILLAGER.get(), entity, visitorUUID);
    }

    public AddRandomUpgradeQuest(RewardType<? extends MCReward> rType, TownInterface flag, RewardsInit reg) {
        this(rType, flag, (VillagerUUID) null);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.getQuestHandle().addRandomUpgradeQuestForVisitor(visitorUUID);
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return true;
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag compoundTag = new CompoundTag();
        visitorUUID.writeToNBT(compoundTag, NBT_VISITOR_UUID);
        return compoundTag;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (tag.contains(NBT_VISITOR_UUID)) {
            this.visitorUUID = VillagerUUID.fromNBT(tag, NBT_VISITOR_UUID);
        }
    }

    @Override
    public String toNiceString() {
        return "AddRandomUpgradeQuest";
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return rType.equals(reward);
    }
}
