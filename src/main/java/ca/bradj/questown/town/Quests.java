package ca.bradj.questown.town;

import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Quests implements INBTSerializable<CompoundTag> {

    private static final String NBT_NUM_QUESTS = "num_quests";
    private static final String NBT_QUESTS = "quests";

    private final List<Quest> activeQuests = new ArrayList<>();

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag ct = new CompoundTag();
        ct.putInt(NBT_NUM_QUESTS, activeQuests.size());
        ListTag aq = new ListTag();
        for (Quest q : activeQuests) {
            aq.add(q.serializeNBT());
        }
        ct.put(NBT_QUESTS, aq);

        return ct;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.activeQuests.clear();
        int num = nbt.getInt(NBT_NUM_QUESTS);
        ListTag aq = nbt.getList(NBT_QUESTS, num);
        for (int i = 0; i < num; i++) {
            Quest q = new Quest();
            CompoundTag tag = aq.getCompound(i);
            q.deserializeNBT(tag);
            this.activeQuests.set(i, q);
        }
    }

    public boolean setStatus(
            ResourceLocation id,
            Quest.QuestStatus questStatus
    ) {
        List<Quest> copy = ImmutableList.copyOf(this.activeQuests);
        for (int i = 0; i < copy.size(); i++) {
            Quest quest = copy.get(i);
            if (id.equals(quest.getId())) {
                quest = quest.withStatus(questStatus);
                this.activeQuests.set(i, quest);
                return true;
            }
        }
        return false;
    }

    public Collection<ResourceLocation> getCompleted() {
        return this.activeQuests.stream().filter(Quest::isComplete).map(Quest::getId).toList();
    }

    public void add(ResourceLocation id) {
        this.activeQuests.add(new Quest(id));
    }

    public ImmutableList<Quest> getAll() {
        return ImmutableList.copyOf(this.activeQuests);
    }
}
