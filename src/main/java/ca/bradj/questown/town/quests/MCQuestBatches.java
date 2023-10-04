package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public class MCQuestBatches extends QuestBatches<ResourceLocation, MCRoom, MCQuest, MCReward, MCQuestBatch> {

    public static final Serializer SERIALIZER = new Serializer();

    public MCQuestBatches(Factory<MCQuestBatch, MCReward> factory) {
        super(factory);
    }

    public ImmutableList<MCQuestBatch> getAllBatches() {
        return ImmutableList.copyOf(this.batches);
    }

    public static class Serializer {
        private static final String NBT_NUM_BATCHES = "num_quest_batches";
        private static final String NBT_BATCHES = "quest_batches";

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

        public void deserializeNBT(TownInterface entity, CompoundTag nbt, MCQuestBatches batches) {
            ImmutableList.Builder<MCQuestBatch> aqs = ImmutableList.builder();
            int num = nbt.getInt(NBT_NUM_BATCHES);
            ListTag aq = nbt.getList(NBT_BATCHES, Tag.TAG_COMPOUND);
            for (int i = 0; i < num; i++) {
                CompoundTag tag = aq.getCompound(i);
                MCQuestBatch q = MCQuestBatch.SERIALIZER.deserializeNBT(entity, tag);
                aqs.add(q);
            }
            batches.initialize(entity, aqs.build());
        }
    }
}
