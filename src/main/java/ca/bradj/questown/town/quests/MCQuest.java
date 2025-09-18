package ca.bradj.questown.town.quests;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.gui.QuestTypes;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

public class MCQuest extends Quest<ResourceLocation, MCRoom> {
    public static final Serializer SERIALIZER = new Serializer();


    MCQuest() {
        super();
    }

    private MCQuest(
            UUID batchUUID,
            @Nullable VillagerUUID ownerId,
            ResourceLocation wantedRecipe,
            @Nullable ResourceLocation fromRecipe
    ) {
        this(batchUUID, ownerId, wantedRecipe, fromRecipe, QuestType.ROOM, 1);
    }

    private MCQuest(
            UUID batchUUID,
            VillagerUUID ownerId,
            ResourceLocation wantedRecipe,
            @Nullable ResourceLocation fromRecipe,
            QuestType questType,
            int count
    ) {
        super(batchUUID, ownerId, wantedRecipe, fromRecipe, questType, count);
    }

    public static MCQuest standalone(
            UUID batchUUID,
            @Nullable VillagerUUID ownerId,
            ResourceLocation recipeId
    ) {
        return new MCQuest(batchUUID, ownerId, recipeId, null, QuestType.ROOM, 1);
    }

    public static MCQuest upgrade(
            UUID batchUUID,
            @Nullable VillagerUUID ownerId,
            ResourceLocation oldRecipeId,
            ResourceLocation newRecipeId
    ) {
        return new MCQuest(batchUUID, ownerId, newRecipeId, oldRecipeId, QuestType.ROOM, 1);
    }

    public static MCQuest item(
            UUID batchUUID,
            @Nullable VillagerUUID ownerId,
            ResourceLocation itemId,
            int count
    ) {
        return new MCQuest(batchUUID, ownerId, itemId, null, QuestType.ITEM, count);
    }

    public static MCQuest jobChange(
            UUID batchUUID,
            @Nullable VillagerUUID ownerId,
            JobID jobId
    ) {
        ResourceLocation rl = JobID.toRL(jobId);
        return jobChange(batchUUID, ownerId, rl);
    }

    public static MCQuest jobChange(
            UUID batchUUID,
            @Nullable VillagerUUID ownerId,
            ResourceLocation jobId
    ) {
        return new MCQuest(batchUUID, ownerId, jobId, null, QuestType.JOB_CHANGE, 1);
    }

    // TODO: Consider changing this to a door instead of a room, since the room can change shape easily
    //  and when the door is removed, the quest is invalidated anyway.
    public MCQuest completed(@Nullable MCRoom room) {
        MCQuest q = new MCQuest(
                this.batchUUID,
                this.ownerUUID,
                this.getWantedId(),
                this.fromRecipeID().orElse(null),
                getType(),
                getCountNeeded()
        );
        q.ownerUUID = this.ownerUUID;
        q.status = QuestStatus.COMPLETED;
        q.completedOn = room;
        return q;
    }

    public MCQuest lost() {
        MCQuest q = new MCQuest(
                this.batchUUID,
                this.ownerUUID,
                this.getWantedId(),
                this.fromRecipeID().orElse(null),
                QuestType.ROOM,
                1
        );
        q.ownerUUID = this.ownerUUID;
        q.status = QuestStatus.ACTIVE; // TODO: Use (and render) "lost" status?
        q.completedOn = null;
        return q;
    }

    public static class Serializer {

        private static final String NBT_UUID = "UUID";
        private static final String NBT_RECIPE_TYPE = "recipe_type";
        private static final String NBT_COUNT = "count";
        private static final String NBT_RECIPE_ID = "recipe_id";
        private static final String NBT_FROM_RECIPE_ID = "from_recipe_id";
        private static final String NBT_STATUS = "status";
        private static final String NBT_COMPLETED_ON_DOORPOS_X = "doorpos_x";
        private static final String NBT_COMPLETED_ON_DOORPOS_Y = "doorpos_y";
        private static final String NBT_COMPLETED_ON_DOORPOS_Z = "doorpos_z";
        private static final String NBT_COMPLETED_ON_AA_X = "aa_x";
        private static final String NBT_COMPLETED_ON_AA_Z = "aa_z";
        private static final String NBT_COMPLETED_ON_BB_X = "bb_x";
        private static final String NBT_COMPLETED_ON_BB_Z = "bb_z";

        public CompoundTag serializeNBT(Quest<ResourceLocation, MCRoom> quest) {
            CompoundTag ct = new CompoundTag();
            if (quest.getUUID() != null) {
                quest.getUUID().writeToNBT(ct, NBT_UUID);
            }
            ct.put(NBT_RECIPE_TYPE, QuestTypes.serializeNBT(quest.getType()));
            ct.putInt(NBT_COUNT, quest.getCountNeeded());

            ct.putString(NBT_RECIPE_ID, quest.getWantedId().toString());
            ct.putString(NBT_STATUS, quest.getStatus().name());

            if (quest.completedOn != null) {
                ct.putInt(NBT_COMPLETED_ON_DOORPOS_X, quest.completedOn.getDoorPos().x);
                ct.putInt(NBT_COMPLETED_ON_DOORPOS_Y, quest.completedOn.yCoord);
                ct.putInt(NBT_COMPLETED_ON_DOORPOS_Z, quest.completedOn.getDoorPos().z);
                putSpace(quest, ct);
            }

            if (quest.fromRecipeID().isPresent()) {
                ct.putString(NBT_FROM_RECIPE_ID, quest.fromRecipeID().get().toString());
            }
            return ct;
        }

        @SuppressWarnings("removal") // See to-do on completed()
        private void putSpace(
                Quest<ResourceLocation, MCRoom> quest,
                CompoundTag ct
        ) {
            ct.putInt(NBT_COMPLETED_ON_AA_X, quest.completedOn.getSpace().getWestX());
            ct.putInt(NBT_COMPLETED_ON_AA_Z, quest.completedOn.getSpace().getNorthZ());
            ct.putInt(NBT_COMPLETED_ON_BB_X, quest.completedOn.getSpace().getEastX());
            ct.putInt(NBT_COMPLETED_ON_BB_Z, quest.completedOn.getSpace().getSouthZ());
        }

        public MCQuest deserializeNBT(CompoundTag nbt) {
            MCQuest quest = new MCQuest();
            @Nullable VillagerUUID uuid = null;
            if (nbt.contains(NBT_UUID)) {
                uuid = VillagerUUID.fromNBT(nbt, NBT_UUID);
            }
            QuestType type = QuestType.ROOM;
            if (nbt.contains(NBT_RECIPE_TYPE)) {
                type = QuestTypes.deserializeNBT(nbt.getCompound(NBT_RECIPE_TYPE));
            }
            int count = 1; // Default count, can be overridden by specific quest types.
            if (nbt.contains(NBT_COUNT)) {
                count = nbt.getInt(NBT_COUNT);
            }
            ResourceLocation recipeId = new ResourceLocation(nbt.getString(NBT_RECIPE_ID));
            QuestStatus status = QuestStatus.valueOf(nbt.getString(NBT_STATUS));
            int doorX = nbt.getInt(NBT_COMPLETED_ON_DOORPOS_X);
            int doorY = nbt.getInt(NBT_COMPLETED_ON_DOORPOS_Y);
            int doorZ = nbt.getInt(NBT_COMPLETED_ON_DOORPOS_Z);
            int aaX = nbt.getInt(NBT_COMPLETED_ON_AA_X);
            int aaZ = nbt.getInt(NBT_COMPLETED_ON_AA_Z);
            int bbX = nbt.getInt(NBT_COMPLETED_ON_BB_X);
            int bbZ = nbt.getInt(NBT_COMPLETED_ON_BB_Z);
            Position doorPos = new Position(doorX, doorZ);
            InclusiveSpace space = InclusiveSpace.from(aaX, aaZ).to(bbX, bbZ);
            ResourceLocation fromRecipeId = null;
            if (nbt.contains(NBT_FROM_RECIPE_ID)) {
                fromRecipeId = new ResourceLocation(nbt.getString(NBT_FROM_RECIPE_ID));
            }
            quest.initialize(
                    uuid, type, count, recipeId, status,
                    new MCRoom(doorPos, ImmutableList.of(space), doorY),
                    fromRecipeId
            );
            return quest;
        }

    }
}
