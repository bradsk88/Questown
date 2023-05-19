package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MCMorningRewards extends MCRewardList {

    private final List<MCReward> currentChildren = new ArrayList<>();

    public MCMorningRewards(
            TownInterface town
    ) {
        super(town);
        this.initializeChildren();
    }

    public void runAll() {
        getApplier().apply();
    }

    public void add(MCReward ev) {
        this.currentChildren.add(ev);
    }

    @Override
    protected void initializeChildren() {
        this.currentChildren.clear();
        this.currentChildren.addAll(super.getChildren());
    }

    @Override
    protected Collection<MCReward> getChildren() {
        return ImmutableList.copyOf(this.currentChildren);
    }

    @Override
    public Tag serializeNbt() {
        return super.serializeNbt();
    }

    @Override
    public void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        super.deserializeNbt(entity, tag);
    }
}
