package ca.bradj.questown.town.quests;

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
            ResourceLocation wantedRecipe,
            @Nullable ResourceLocation fromRecipe
    ) {
        super(wantedRecipe, fromRecipe);
    }

    public static MCQuest standalone(ResourceLocation recipeId) {
        return new MCQuest(recipeId, null);
    }

    public static MCQuest upgrade(ResourceLocation oldRecipeId, ResourceLocation newRecipeId) {
        return new MCQuest(newRecipeId, oldRecipeId);
    }

    public MCQuest completed(MCRoom room) {
        MCQuest q = new MCQuest(this.getWantedId(), this.fromRecipeID().orElse(null));
        q.uuid = this.uuid;
        q.status = QuestStatus.COMPLETED;
        q.completedOn = room;
        return q;
    }

    public MCQuest lost() {
        MCQuest q = new MCQuest(this.getWantedId(), this.fromRecipeID().orElse(null));
        q.uuid = this.uuid;
        q.status = QuestStatus.ACTIVE; // TODO: Use (and render) "lost" status?
        q.completedOn = null;
        return q;
    }

    public static class Serializer {

        private static final String NBT_UUID = "uuid";
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
            ct.putUUID(NBT_UUID, quest.getUUID());
            ct.putString(NBT_RECIPE_ID, quest.getWantedId().toString());
            ct.putString(NBT_STATUS, quest.getStatus().name());

            if (quest.completedOn != null) {
                ct.putInt(NBT_COMPLETED_ON_DOORPOS_X, quest.completedOn.getDoorPos().x);
                ct.putInt(NBT_COMPLETED_ON_DOORPOS_Y, quest.completedOn.yCoord);
                ct.putInt(NBT_COMPLETED_ON_DOORPOS_Z, quest.completedOn.getDoorPos().z);
                ct.putInt(NBT_COMPLETED_ON_AA_X, quest.completedOn.getSpace().getWestX());
                ct.putInt(NBT_COMPLETED_ON_AA_Z, quest.completedOn.getSpace().getNorthZ());
                ct.putInt(NBT_COMPLETED_ON_BB_X, quest.completedOn.getSpace().getEastX());
                ct.putInt(NBT_COMPLETED_ON_BB_Z, quest.completedOn.getSpace().getSouthZ());
            }

            if (quest.fromRecipeID().isPresent()) {
                ct.putString(NBT_FROM_RECIPE_ID, quest.fromRecipeID().get().toString());
            }
            return ct;
        }

        public MCQuest deserializeNBT(CompoundTag nbt) {
            MCQuest quest = new MCQuest();
            UUID uuid = nbt.getUUID(NBT_UUID);
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
            InclusiveSpace space = new InclusiveSpace(new Position(aaX, aaZ), new Position(bbX, bbZ));
            ResourceLocation fromRecipeId = null;
            if (nbt.contains(NBT_FROM_RECIPE_ID)) {
                fromRecipeId = new ResourceLocation(nbt.getString(NBT_FROM_RECIPE_ID));
            }
            quest.initialize(
                    uuid, recipeId, status,
                    new MCRoom(doorPos, ImmutableList.of(space), doorY),
                    fromRecipeId
            );
            return quest;
        }

    }
}
