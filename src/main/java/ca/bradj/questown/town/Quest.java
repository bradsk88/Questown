package ca.bradj.questown.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.UUID;

public class Quest implements INBTSerializable<CompoundTag> {

    private static final String NBT_UUID = "uuid";
    private static final String NBT_RECIPE_ID = "recipe_id";
    private static final String NBT_STATUS = "status";

    private UUID uuid;

    public ResourceLocation getId() {
        return recipeId;
    }

    public Quest withStatus(QuestStatus questStatus) {
        Quest quest = new Quest(recipeId);
        quest.status = questStatus;
        quest.uuid = uuid;
        return quest;
    }

    public boolean isComplete() {
        return QuestStatus.COMPLETED.equals(status);
    }

    public QuestStatus getStatus() {
        return status;
    }

    public enum QuestStatus {
        UNSET(""),
        ACTIVE("active"),
        COMPLETED("completed");

        private final String str;

        QuestStatus(String str) {
            this.str = str;
        }
    }

    Quest() {
        this(null);
    }
    public Quest(ResourceLocation recipe) {
        this.uuid = UUID.randomUUID();
        this.recipeId = recipe;
        this.status = QuestStatus.ACTIVE;
    }

    private ResourceLocation recipeId;

    private QuestStatus status;

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag ct = new CompoundTag();
        ct.putUUID(NBT_UUID, uuid);
        ct.putString(NBT_RECIPE_ID, recipeId.toString());
        ct.putString(NBT_STATUS, status.str);
        return ct;
    };

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.uuid = nbt.getUUID(NBT_UUID);
        this.recipeId = new ResourceLocation(nbt.getString(NBT_RECIPE_ID));
        this.status = QuestStatus.valueOf(nbt.getString(NBT_STATUS));
    }


}
