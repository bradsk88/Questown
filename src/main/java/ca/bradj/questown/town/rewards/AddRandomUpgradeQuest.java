package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AddRandomUpgradeQuest extends MCReward {

    public static final String ID = "add_random_upgrade_quest";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private final TownInterface town;
    private UUID visitorUUID;

    private AddRandomUpgradeQuest(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            UUID visitorUUID // Allowed to be null because rewards get deserialized at runtime.
    ) {
        super(rType);
        this.visitorUUID = visitorUUID;
        this.town = entity;
    }

    public AddRandomUpgradeQuest(
            @NotNull TownInterface entity,
            @NotNull UUID visitorUUID
    ) {
        this(RewardsInit.RANDOM_UPGRADE_FOR_VILLAGER.get(), entity, visitorUUID);
    }

    public AddRandomUpgradeQuest(RewardType<? extends MCReward> rType, TownInterface flag, RewardsInit reg) {
        this(rType, flag, (UUID) null);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.addRandomUpgradeQuestForVisitor(visitorUUID);
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putUUID(NBT_VISITOR_UUID, this.visitorUUID);
        return compoundTag;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (tag.contains(NBT_VISITOR_UUID)) {
            this.visitorUUID = tag.getUUID(NBT_VISITOR_UUID);
        }
    }
}
