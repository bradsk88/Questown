package ca.bradj.questown.town.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The onboarding marker gates villager arrivals (TownQuests.isArrivalGateOpen). village/day1
 * 2026-10-17: after a real flag reload every quest read onboarding=false, so villager 2 arrived
 * before the raw-food quest was complete.
 */
class MCQuestOnboardingPersistenceTest {

    private static final ResourceLocation GATE = new ResourceLocation("questown", "special_quest.town_gate");

    @Test
    void nbtRoundTrip_keepsTheOnboardingMarker() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, GATE);
        q.setOnboarding(true);
        MCQuest restored = MCQuest.SERIALIZER.deserializeNBT(MCQuest.SERIALIZER.serializeNBT(q));
        assertTrue(restored.isOnboarding());
    }

    @Test
    void nbtRoundTrip_ofACompletedQuest_keepsTheOnboardingMarker() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, GATE);
        q.setOnboarding(true);
        MCQuest done = q.completed(null);
        CompoundTag nbt = MCQuest.SERIALIZER.serializeNBT(done);
        assertTrue(MCQuest.SERIALIZER.deserializeNBT(nbt).isOnboarding());
    }

    @Test
    void nbtRoundTrip_ofANonOnboardingQuest_staysUnmarked() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, GATE);
        assertFalse(MCQuest.SERIALIZER.deserializeNBT(MCQuest.SERIALIZER.serializeNBT(q)).isOnboarding());
    }
}
