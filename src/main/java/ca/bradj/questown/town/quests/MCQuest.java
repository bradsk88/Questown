package ca.bradj.questown.town.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class MCQuest extends Quest<ResourceLocation> {
    public static final Serializer SERIALIZER = new Serializer();

    MCQuest() {
        super();
    }
    public MCQuest(ResourceLocation recipe) {
        super(recipe);
    }

    public MCQuest withStatus(QuestStatus status) {
        MCQuest q = new MCQuest(this.getId());
        q.uuid = this.uuid;
        q.status = status;
        return q;
    }

    public static class Serializer {

        private static final String NBT_UUID = "uuid";
        private static final String NBT_RECIPE_ID = "recipe_id";
        private static final String NBT_STATUS = "status";

        public CompoundTag serializeNBT(Quest<ResourceLocation> quest) {
            CompoundTag ct = new CompoundTag();
            ct.putUUID(NBT_UUID, quest.getUUID());
            ct.putString(NBT_RECIPE_ID, quest.getId().toString());
            ct.putString(NBT_STATUS, quest.getStatus().name());
            return ct;
        }

        public void deserializeNBT(CompoundTag nbt, Quest<ResourceLocation> quest) {
            UUID uuid = nbt.getUUID(NBT_UUID);
            ResourceLocation recipeId = new ResourceLocation(nbt.getString(NBT_RECIPE_ID));
            QuestStatus status = QuestStatus.valueOf(nbt.getString(NBT_STATUS));
            quest.initialize(uuid, recipeId, status);
        }

    }
}
