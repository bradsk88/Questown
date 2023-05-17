package ca.bradj.questown.town.rewards;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AddBatchOfRandomQuestsForVisitorReward extends MCReward {

    public static final String ID = "add_random_batch_of_quests";
    private static final String NBT_VISITOR_UUID = "visitor_uuid";
    private final TownInterface town;
    private UUID visitorUUID;

    public AddBatchOfRandomQuestsForVisitorReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            @NotNull UUID visitorUUID
    ) {
        super(rType);
        this.visitorUUID = visitorUUID;
        this.town = entity;
    }

    public AddBatchOfRandomQuestsForVisitorReward(
            @NotNull TownInterface entity,
            @NotNull UUID visitorUUID
    ) {
        this(RewardsInit.RANDOM_BATCH_FOR_VILLAGER.get(), entity, visitorUUID);
    }

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> town.addBatchOfRandomQuestsForVisitor(visitorUUID);
    }

    @Override
    protected Tag serializeNbt() {
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
