package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class AddItemQuestReward extends MCReward {
    public static final String ID = "add_item_quest_reward";
    private final TownInterface town;
    private ResourceLocation itemId;
    private int count;

    public AddItemQuestReward(
            TownInterface town,
            ResourceLocation itemIdNotTag,
            int count
    ) {
        this(RewardsInit.ITEM_QUEST.get(), town);
        this.itemId = itemIdNotTag;
        this.count = count;
    }

    private AddItemQuestReward(
            RewardType<AddItemQuestReward> type,
            TownInterface town
    ) {
        super(type);
        this.town = town;
    }

    @Override
    public String toNiceString() {
        return count + "x " + itemId.toString();
    }

    @Override
    public boolean contains(@NotNull RewardType<?> reward) {
        return rType.equals(reward);
    }

    @Override
    public boolean addsQuestsWhenApplied() {
        return false;
    }

    @Override
    protected CompoundTag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", count);
        tag.putString("item_id", itemId.toString());
        return tag;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        this.count = tag.getInt("count");
        this.itemId = ResourceLocation.tryParse(tag.getString("item_id"));
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.getQuestHandle().addItemQuest(itemId, count);
    }

    @SuppressWarnings("unchecked")
    public static AddItemQuestReward emptyForDeserializing(
            RewardType<? extends MCReward> rewardType,
            TownInterface townInterface
    ) {
        return new AddItemQuestReward((RewardType<AddItemQuestReward>) rewardType, townInterface);
    }
}
