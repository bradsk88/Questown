package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// MCQuests is a simple wrapper for Quests that is coupled to Minecraft
public class MCQuestBatch extends QuestBatch<ResourceLocation, MCQuest, MCReward> {
    public static final Serializer SERIALIZER = new Serializer();
    private UUID owner;

    MCQuestBatch() {
        this(null, null);
    }

    public MCQuestBatch(@Nullable UUID owner, MCReward reward) {
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
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public static class Serializer {
        private static final String NBT_NUM_QUESTS = "num_quests";
        private static final String NBT_QUESTS = "quests";
        private static final String NBT_REWARD = "reward";
        private static final String NBT_OWNER_UUID = "owner_uuid";

        public CompoundTag serializeNBT(
                MCQuestBatch quests
        ) {
            CompoundTag ct = new CompoundTag();
            if (quests.getOwner() != null) {
                ct.putUUID(NBT_OWNER_UUID, quests.getOwner());
            }
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

        public MCQuestBatch deserializeNBT(TownInterface entity, CompoundTag nbt) {
            MCQuestBatch quests = new MCQuestBatch();
            if (nbt.contains(NBT_OWNER_UUID)) {
                quests.owner = nbt.getUUID(NBT_OWNER_UUID);
            }
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
            return quests;
        }
    }
}
