package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// MCQuests is a simple wrapper for Quests that is coupled to Minecraft
public class MCQuestBatch extends QuestBatch<ResourceLocation, MCRoom, MCQuest, MCReward> {
    public static final Serializer SERIALIZER = new Serializer();
    private UUID owner;

    MCQuestBatch() {
        this(null, null);
    }

    public MCQuestBatch(@Nullable UUID owner, MCReward reward) {
        super(new Quest.QuestFactory<>() {
            @Override
            public MCQuest newQuest(@Nullable UUID ownerID, ResourceLocation recipeId) {
                return MCQuest.standalone(ownerID, recipeId);
            }

            @Override
            public MCQuest newUpgradeQuest(@Nullable UUID ownerID, ResourceLocation oldRecipeId, ResourceLocation newRecipeId) {
                return MCQuest.upgrade(ownerID, oldRecipeId, newRecipeId);
            }

            @Override
            public MCQuest completed(MCRoom room, MCQuest input) {
                return input.completed(room);
            }

            @Override
            public MCQuest lost(MCQuest foundQuest) {
                return foundQuest.lost();
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
                CompoundTag tag = aq.getCompound(i);
                MCQuest q = MCQuest.SERIALIZER.deserializeNBT(tag);
                aqs.add(q);
            }
            MCReward reward = MCReward.SERIALIZER.deserializeNBT(entity, nbt.getCompound(NBT_REWARD));
            quests.initialize(aqs.build(), reward);
            return quests;
        }
    }
}
