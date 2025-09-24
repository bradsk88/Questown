package ca.bradj.questown.town.quests;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import joptsimple.internal.Strings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// MCQuests is a simple wrapper for Quests that is coupled to Minecraft
public class MCQuestBatch extends QuestBatch<ResourceLocation, MCRoom, MCQuest, MCReward> {
    public static final Serializer SERIALIZER = new Serializer();
    private VillagerUUID owner;

    private static Quest.QuestFactory<ResourceLocation, MCRoom, MCQuest> FACTORY(
            UUID batchUUID
    ) {
        return new Quest.QuestFactory<>() {
            @Override
            public MCQuest newQuest(
                    @Nullable UUID ownerID,
                    ResourceLocation recipeId
            ) {
                return MCQuest.standalone(batchUUID, dep(ownerID), recipeId);
            }

            @Override
            public MCQuest newUpgradeQuest(
                    @Nullable UUID ownerID,
                    ResourceLocation oldRecipeId,
                    ResourceLocation newRecipeId
            ) {
                return MCQuest.upgrade(batchUUID, dep(ownerID), oldRecipeId, newRecipeId);
            }

            @Override
            public MCQuest newItemQuest(
                    @Nullable UUID ownerId,
                    ResourceLocation itemId,
                    int count
            ) {
                return MCQuest.item(batchUUID, dep(ownerId), itemId, count);
            }

            @Override
            public MCQuest newJobQuest(ResourceLocation id) {
                return MCQuest.jobChange(batchUUID, null, id);
            }

            @Override
            public MCQuest completed(
                    @Nullable MCRoom room,
                    MCQuest input
            ) {
                return input.completed(room);
            }

            @Override
            public MCQuest lost(MCQuest foundQuest) {
                return foundQuest.lost();
            }
        };
    }

    ;

    MCQuestBatch() {
        this(null, null, null);
    }

    public void setOwner(VillagerUUID owner) {
        this.owner = owner;
    }

    public static final class Inputs {
        private final UUID batchUUID;
        private final @Nullable VillagerUUID owner;
        private final List<MCQuest> quests = new ArrayList<>();

        public Inputs(
                UUID batchUUID,
                @Nullable VillagerUUID owner
        ) {
            this.batchUUID = batchUUID;
            this.owner = owner;
        }

        public MCQuestBatch withRewardUponCompletion(@NotNull MCReward reward) {
            MCQuestBatch mcQuestBatch = new MCQuestBatch();
            mcQuestBatch.initialize(batchUUID, quests, reward);
            return mcQuestBatch;
        }

        public void addNewQuest(
                MCQuest q
        ) {
            quests.add(q);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Inputs) obj;
            return Objects.equals(this.batchUUID, that.batchUUID) && Objects.equals(this.owner, that.owner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(batchUUID, owner);
        }

        @Override
        public String toString() {
            return "Inputs[" + "batchUUID=" + batchUUID + ", " + "owner=" + owner + ']';
        }
    }

    /**
     * @deprecated Use Inputs.withRewardUponCompletion for improved readability
     */
    @Deprecated(forRemoval = true)
    public MCQuestBatch(
            UUID batchUUID,
            @Nullable VillagerUUID owner,
            @NotNull MCReward reward
            // TODO: Allow null once this goes private
    ) {
        super(FACTORY(batchUUID), reward, batchUUID);
        this.owner = owner;
    }

    private static @Nullable VillagerUUID dep(@Nullable UUID ownerID) {
        return VillagerUUID.from(ownerID);
    }

    public VillagerUUID getOwner() {
        return owner;
    }

    @Override
    public void assignTo(@NotNull VillagerUUID owner) {
        this.owner = owner;
        super.assignTo(owner);
    }

    public String toNiceString() {
        return String.format(
                "%s from [%s]",
                reward.toNiceString(),
                Strings.join(this.getAll().stream().map(Quest::toShortString).toList(), ",")
        );
    }

    public static class Serializer {
        private static final String NBT_NUM_QUESTS = "num_quests";
        private static final String NBT_QUESTS = "quests";
        private static final String NBT_REWARD = "reward";
        private static final String NBT_OWNER_UUID = "owner_uuid";
        private static final String NBT_BATCH_UUID = "batch_uuid";

        public CompoundTag serializeNBT(
                MCQuestBatch quests
        ) {
            CompoundTag ct = new CompoundTag();
            if (quests.getOwner() != null) {
                quests.getOwner().writeToNBT(ct, NBT_OWNER_UUID);
            }
            ImmutableList<MCQuest> aqs = quests.getAll();
            ct.putInt(NBT_NUM_QUESTS, aqs.size());
            ListTag aq = new ListTag();
            for (MCQuest q : aqs) {
                aq.add(MCQuest.SERIALIZER.serializeNBT(q));
            }
            ct.put(NBT_QUESTS, aq);
            ct.put(NBT_REWARD, MCReward.SERIALIZER.serializeNBT(quests.reward));
            if (quests.getBatchUUID() != null) {
                ct.putUUID(NBT_BATCH_UUID, quests.getBatchUUID());
            }
            return ct;
        }

        public MCQuestBatch deserializeNBT(
                TownInterface entity,
                CompoundTag nbt
        ) {
            MCQuestBatch quests = new MCQuestBatch();
            if (nbt.contains(NBT_OWNER_UUID)) {
                quests.owner = VillagerUUID.fromNBT(nbt, NBT_OWNER_UUID);
            }
            ImmutableList.Builder<MCQuest> aqs = ImmutableList.builder();
            int num = nbt.getInt(NBT_NUM_QUESTS);
            ListTag aq = nbt.getList(NBT_QUESTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < num; i++) {
                CompoundTag tag = aq.getCompound(i);
                MCQuest q = MCQuest.SERIALIZER.deserializeNBT(tag);
                aqs.add(q);
            }
            ImmutableList<MCQuest> allQuests = aqs.build();
            UUID batchUUID = backwardsCompatibleBatchUUIDLoader(nbt, allQuests);
            MCReward reward = MCReward.SERIALIZER.deserializeNBT(entity, nbt.getCompound(NBT_REWARD));
            quests.initialize(batchUUID, allQuests, reward);
            return quests;
        }

        @Nullable
        private static UUID backwardsCompatibleBatchUUIDLoader(
                CompoundTag nbt,
                ImmutableList<MCQuest> allQuests
        ) {
            UUID batchUUID = null;
            if (nbt.contains(NBT_BATCH_UUID)) {
                batchUUID = nbt.getUUID(NBT_BATCH_UUID);
            }
            if (batchUUID == null) {
                if (allQuests.size() == 1 && SpecialQuests.isSpecialQuest(allQuests.get(0).getWantedId())) {
                    return null;
                }
                batchUUID = UUID.randomUUID();
                QT.QUESTS_LOGGER.warn(
                        "[Backwards Compatibility] Generating UUID for quest batch with missing UUID: {}",
                        batchUUID
                );
            }
            return batchUUID;
        }
    }

    @Override
    public String getCompletionMessage() {
        if (reward.contains(RewardsInit.VISITOR.get())) {
            return "dialog.visitors.instruction.sleep_visitors";
        }
        if (reward instanceof MCInstantReward) {
            if (reward.contains(RewardsInit.RANDOM_BATCH_FOR_VILLAGER.get())) {
                return "dialog.visitors.instruction.new_quests_now";
            }
        }
        return null;
    }
}
