package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.quests.Quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public class QuestTypes {
    public static void toNetwork(
            FriendlyByteBuf buf,
            Quest.QuestType type
    ) {
        buf.writeUtf(type.name());
    }

    public static Quest.QuestType fromNetwork(FriendlyByteBuf buf) {
        return Quest.QuestType.valueOf(buf.readUtf());
    }

    public static Quest.QuestType deserializeNBT(CompoundTag compound) {
        if (!compound.contains("name", Tag.TAG_STRING)) {
            QT.INIT_LOGGER.error("Quest type NBT does not contain 'name' tag. Defaulting to ROOM type.");
            return Quest.QuestType.ROOM;
        }
        return Quest.QuestType.valueOf(compound.getString("name"));
    }

    public static Tag serializeNBT(Quest.QuestType type) {
        return new CompoundTag() {{
            putString("name", type.name());
        }};
    }
}
