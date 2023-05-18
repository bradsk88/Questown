package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.event.world.SleepFinishedTimeEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public class MCMorningRewards extends MCRewardList implements Consumer<SleepFinishedTimeEvent> {

    private final List<MCReward> currentChildren = new ArrayList<>();

    public MCMorningRewards(
            TownInterface town
    ) {
        super(town);
        this.initializeChildren();
    }

    @Override
    public void accept(SleepFinishedTimeEvent t) {
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
