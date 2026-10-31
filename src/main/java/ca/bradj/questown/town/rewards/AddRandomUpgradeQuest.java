package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import com.google.common.collect.ImmutableSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AddRandomUpgradeQuest extends MCReward {

    public static final String ID = "add_random_upgrade_quest";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private static final String NBT_DECLINED_FROM = "declined_from";
    private final TownInterface town;
    private VillagerUUID visitorUUID;
    /** Rooms whose upgrade the player just declined; this upgrade skips them (dev 2026-10-24). */
    private ImmutableSet<ResourceLocation> declinedFrom = ImmutableSet.of();

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

    /** A discard's replacement: an upgrade for {@code visitorUUID} that skips {@code declinedFrom}. */
    public AddRandomUpgradeQuest(
            @NotNull TownInterface entity,
            @NotNull VillagerUUID visitorUUID,
            ImmutableSet<ResourceLocation> declinedFrom
    ) {
        this(RewardsInit.RANDOM_UPGRADE_FOR_VILLAGER.get(), entity, visitorUUID);
        this.declinedFrom = declinedFrom;
    }

    public AddRandomUpgradeQuest(RewardType<? extends MCReward> rType, TownInterface flag, RewardsInit reg) {
        this(rType, flag, (VillagerUUID) null);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.getQuestHandle().addRandomUpgradeQuestForVisitor(visitorUUID, declinedFrom);
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return true;
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag compoundTag = new CompoundTag();
        visitorUUID.writeToNBT(compoundTag, NBT_VISITOR_UUID);
        ListTag declined = new ListTag();
        declinedFrom.forEach(id -> declined.add(StringTag.valueOf(id.toString())));
        compoundTag.put(NBT_DECLINED_FROM, declined);
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
        ImmutableSet.Builder<ResourceLocation> declined = ImmutableSet.builder();
        tag.getList(NBT_DECLINED_FROM, Tag.TAG_STRING)
                .forEach(t -> declined.add(new ResourceLocation(t.getAsString())));
        this.declinedFrom = declined.build();
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
