package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

// MCQuests is a simple wrapper for Quests that is coupled to Minecraft
public class MCQuestBatch extends QuestBatch<ResourceLocation, MCQuest, MCReward> {
    public static final Serializer SERIALIZER = new Serializer();

    MCQuestBatch() {
        this(null);
    }

    public MCQuestBatch(MCReward reward) {
        super(new Quest.QuestFactory<>() {
            @Override
            public MCQuest newQuest(ResourceLocation recipeId) {
                return new MCQuest(recipeId);
            }

            @Override
            public MCQuest withStatus(
                    MCQuest input,
                    Quest.QuestStatus status
            ) {
                return input.withStatus(status);
            }
        }, reward);
    }

    public static class Serializer {
        private static final String NBT_NUM_QUESTS = "num_quests";
        private static final String NBT_QUESTS = "quests";
        private static final String NBT_REWARD = "reward";

        public CompoundTag serializeNBT(MCQuestBatch quests) {
            CompoundTag ct = new CompoundTag();
            ImmutableList<MCQuest> aqs = quests.getAll();
            ct.putInt(NBT_NUM_QUESTS, aqs.size());
            ListTag aq = new ListTag();
            for (MCQuest q : aqs) {
                aq.add(MCQuest.SERIALIZER.serializeNBT(q));
            }
            ct.put(NBT_QUESTS, aq);
            ct.put(NBT_REWARD, MCReward.SERIALIZER.serializeNBT(quests.reward));
            return ct;
        }

        public void deserializeNBT(TownInterface entity, CompoundTag nbt, MCQuestBatch quests) {
            ImmutableList.Builder<MCQuest> aqs = ImmutableList.builder();
            int num = nbt.getInt(NBT_NUM_QUESTS);
            ListTag aq = nbt.getList(NBT_QUESTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < num; i++) {
                MCQuest q = new MCQuest();
                CompoundTag tag = aq.getCompound(i);
                MCQuest.SERIALIZER.deserializeNBT(tag, q);
                aqs.add(q);
            }
            MCReward reward = MCReward.SERIALIZER.deserializeNBT(entity, nbt.getCompound(NBT_REWARD));
            quests.initialize(aqs.build(), reward);
        }
    }
}
