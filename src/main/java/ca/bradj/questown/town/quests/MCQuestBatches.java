package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public class MCQuestBatches extends QuestBatches<ResourceLocation, MCQuest, MCQuestBatch> {

    public static final Serializer SERIALIZER = new Serializer();

    public static class Serializer {
        private static final String NBT_NUM_BATCHES = "num_quest_batches";
        private static final String NBT_BATCHES = "quest_batches";
        private static final String NBT_REWARD_TYPE = "reward_type";

        public CompoundTag serializeNBT(MCQuestBatches batches) {
            CompoundTag ct = new CompoundTag();
            ImmutableList<MCQuestBatch> qb = ImmutableList.copyOf(batches.batches);
            ct.putInt(NBT_NUM_BATCHES, qb.size());
            ListTag aq = new ListTag();
            for (MCQuestBatch q : qb) {
                aq.add(MCQuestBatch.SERIALIZER.serializeNBT(q));
            }
            ct.put(NBT_BATCHES, aq);

            return ct;
        }

        public void deserializeNBT(TownFlagBlockEntity entity, CompoundTag nbt, MCQuestBatches batches) {
            ImmutableList.Builder<MCQuestBatch> aqs = ImmutableList.builder();
            int num = nbt.getInt(NBT_NUM_BATCHES);
            ListTag aq = nbt.getList(NBT_BATCHES, Tag.TAG_COMPOUND);
            for (int i = 0; i < num; i++) {
                CompoundTag tag = aq.getCompound(i);
                MCQuestBatch q = new MCQuestBatch();
                MCQuestBatch.SERIALIZER.deserializeNBT(entity, tag, q);
                aqs.add(q);
            }
            batches.initialize(aqs.build());
        }
    }
}
