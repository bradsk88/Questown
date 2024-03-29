package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rewards.RewardType;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class MCRewardList extends MCReward implements MCRewardContainer {

    public static final String ID = "list";
    private static final String NBT_CHILDREN = "children";

    public ImmutableList<MCReward> children;

    public MCRewardList(
            RewardType<? extends MCReward> rType,
            TownInterface town,
            MCReward... children
    ) {
        super(rType);
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

    @Override
    protected @NotNull RewardApplier getApplier() {
        return () -> {
            QT.QUESTS_LOGGER.debug("Applying quests: {}", getChildren());
            for (Reward r : getChildren()) {
                r.claim();
            }
        };
    }

    @Override
    protected Tag serializeNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag cs = new ListTag();
        for (MCReward r : this.getChildren()) {
            cs.add(MCReward.SERIALIZER.serializeNBT(r));
        }
        tag.put(NBT_CHILDREN, cs);
        return tag;
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        if (this.getChildren() != null && this.getChildren().size() > 0) {
            throw new IllegalStateException("Already initialized");
        }
        ListTag l = tag.getList(NBT_CHILDREN, Tag.TAG_COMPOUND);
        ImmutableList.Builder<MCReward> b = ImmutableList.builder();
        for (int i = 0; i < l.size(); i++) {
            b.add(MCReward.SERIALIZER.deserializeNBT(entity, l.getCompound(i)));
        }
        ImmutableList<MCReward> cz = b.build();
        this.children = cz;
        this.initializeChildren();
    }

    protected void initializeChildren() {
    }

    public Collection<MCReward> getChildren() {
        return children;
    }

    @Override
    public Collection<MCReward> getContainedRewards() {
        return ImmutableList.copyOf(children);
    }

    @Override
    public String toString() {
        return "MCRewardList{" +
                // Log4J doesn't like when this contains newlines
                "children=" + String.join(", ", children.stream().map(Object::toString).toList()) +
                '}';
    }
}
