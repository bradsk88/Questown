package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rewards.RewardType;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Collection;
import java.util.List;

public class MCRewardList extends MCReward {

    public static final String ID = "list";
    private static final String NBT_CHILDREN = "children";

    private List<? extends MCReward> children;

    public MCRewardList(
            RewardType<? extends MCReward> rType,
            TownInterface town,
            MCReward... children
    ) {
        super(rType, () -> {
            for (Reward r : children) {
                r.claim();
            }
        });
        this.children = ImmutableList.copyOf(children);
    }

    public MCRewardList(
            TownInterface town,
            Collection<? extends MCReward> children
    ) {
        this(RewardsInit.LIST.get(), town, children.toArray(new MCReward[0]));
    }

    public MCRewardList(
            TownInterface town,
            MCReward... children
    ) {
        this(RewardsInit.LIST.get(), town, children);
    }

    protected Tag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag cs = new ListTag();
        for (MCReward r : this.children) {
            cs.add(MCReward.SERIALIZER.serializeNBT(r));
        }
        tag.put(NBT_CHILDREN, cs);
        return tag;
    }

    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (this.children != null && this.children.size() > 0) {
            throw new IllegalStateException("Already initialized");
        }
        ListTag l = tag.getList(NBT_CHILDREN, Tag.TAG_COMPOUND);
        ImmutableList.Builder<MCReward> b = ImmutableList.builder();
        for (int i = 0; i < l.size(); i++) {
            b.add(MCReward.SERIALIZER.deserializeNBT(entity, l.getCompound(i)));
        }
        this.children = b.build();
    }
}
