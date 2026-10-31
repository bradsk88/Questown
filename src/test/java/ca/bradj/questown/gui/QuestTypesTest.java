package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Adding LOOT to QuestType must round-trip through the NBT and network codecs with no
// codec change: both sides use name() / valueOf, and old saves (which never contain LOOT)
// stay valid.
class QuestTypesTest {

    @Test
    void lootRoundTripsThroughNBT() {
        assertEquals(
                Quest.QuestType.LOOT,
                QuestTypes.deserializeNBT((CompoundTag) QuestTypes.serializeNBT(Quest.QuestType.LOOT))
        );
    }

    @Test
    void lootRoundTripsThroughNetwork() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        QuestTypes.toNetwork(buf, Quest.QuestType.LOOT);
        assertEquals(Quest.QuestType.LOOT, QuestTypes.fromNetwork(buf));
    }

    @Test
    void existingTypesStillRoundTripThroughNBT() {
        for (Quest.QuestType type : Quest.QuestType.values()) {
            if (type == Quest.QuestType.UNKNOWN) {
                continue; // UNKNOWN is a sentinel, never serialized by a real quest
            }
            assertEquals(type, QuestTypes.deserializeNBT((CompoundTag) QuestTypes.serializeNBT(type)), type.name());
        }
    }
}
