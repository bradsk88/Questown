package ca.bradj.questown.town;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

public class PendingQuestsSerializer {

    public static final PendingQuestsSerializer INSTANCE = new PendingQuestsSerializer();

    private static final String NBT_NUM_BATCHES = "num_batches";
    private static final String NBT_BATCHES = "batches";
    private static final String NBT_WEIGHT_THRESHOLD = "weight_threshold";

    public CompoundTag serializeNBT(
        Collection<PendingQuests> batches
    ) {
        CompoundTag ct = new CompoundTag();
        ct.putInt(NBT_NUM_BATCHES, batches.size());
        ListTag aq = new ListTag();
        for (PendingQuests q : batches) {
            MCQuestBatch asBatch = new MCQuestBatch(q.batch.getOwner(), q.batch.getReward());
            q.batch.getAll().forEach(v -> asBatch.addNewQuest(v.getId()));
            CompoundTag tag = MCQuestBatch.SERIALIZER.serializeNBT(asBatch);
            tag.putInt(NBT_WEIGHT_THRESHOLD, q.maxItemWeight);
            aq.add(tag);
        }
        ct.put(NBT_BATCHES, aq);

        return ct;
    }

    public Collection<PendingQuests> deserializeNBT(
            TownInterface entity, CompoundTag nbt
    ) {

        ImmutableList.Builder<PendingQuests> aqs = ImmutableList.builder();
        int num = nbt.getInt(NBT_NUM_BATCHES);
        ListTag aq = nbt.getList(NBT_BATCHES, Tag.TAG_COMPOUND);
        for (int i = 0; i < num; i++) {
            CompoundTag tag = aq.getCompound(i);
            MCQuestBatch b = MCQuestBatch.SERIALIZER.deserializeNBT(entity, tag);
            int threshold = tag.getInt(NBT_WEIGHT_THRESHOLD);
            PendingQuests pendingQuests = new PendingQuests(threshold, b.getOwner(), b.getReward());
            for (Quest<ResourceLocation> q : b.getAll()) {
                pendingQuests.batch.addNewQuest(q.getId());
            }
            aqs.add(pendingQuests);
        }

        return aqs.build();
    }
}
