package ca.bradj.questown.town.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the LOOT-quest extraction counter behind the 2026-10-15 accumulation
 * ruling (reversing the 2026-10-04 close-on-first-extraction): the quest's
 * "produced so far" total accumulates across extraction events and must
 * survive world saves (the NBT round trip), defaulting to 0 on legacy saves.
 */
class MCQuestLootProgressTest {

    private static final ResourceLocation FOOD = new ResourceLocation("questown", "villager_food");

    @Test
    void addExtracted_accumulatesAndReportsTheRunningTotal() {
        MCQuest q = MCQuest.loot(UUID.randomUUID(), null, FOOD, 8);
        assertEquals(0, q.getExtracted());
        assertFalse(q.addExtracted(1) >= q.getCountNeeded());
        assertEquals(1, q.getExtracted());
        assertEquals(3, q.addExtracted(2));
        assertEquals(8, q.addExtracted(5), "overshoot reports the true total");
        assertTrue(q.addExtracted(0) >= q.getCountNeeded());
    }

    @Test
    void nbtRoundTrip_preservesTheExtractedTotal() {
        MCQuest q = MCQuest.loot(UUID.randomUUID(), null, FOOD, 8);
        q.addExtracted(5);
        CompoundTag nbt = MCQuest.SERIALIZER.serializeNBT(q);
        MCQuest restored = MCQuest.SERIALIZER.deserializeNBT(nbt);
        assertEquals(5, restored.getExtracted(), "extraction progress must survive a save");
        assertEquals(8, restored.getCountNeeded());
        assertFalse(restored.isComplete());
    }

    @Test
    void legacyNbtWithoutTheField_defaultsToZero() {
        MCQuest q = MCQuest.loot(UUID.randomUUID(), null, FOOD, 8);
        CompoundTag nbt = MCQuest.SERIALIZER.serializeNBT(q);
        MCQuest restored = MCQuest.SERIALIZER.deserializeNBT(nbt);
        assertEquals(0, restored.getExtracted(), "a save from before the counter defaults to 0");
    }
}
