package ca.bradj.questown.town.quests;

import ca.bradj.questown.gui.UIQuest;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-21 agent playtest: the onboarding "Town Gate" card (even once completed) offered a
 * "Remove request" x. Only procedurally generated batches are discardable (the player trades one
 * for another, TownQuests.playerDiscardedLastBatch); designed and tutorial batches are not.
 */
class MCQuestDiscardableTest {

    private static final ResourceLocation GATE = new ResourceLocation("questown", "special_quest.town_gate");

    @Test
    void aQuestIsNotDiscardableByDefault() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, GATE);
        assertFalse(q.isDiscardable());
    }

    @Test
    void nbtRoundTrip_keepsTheDiscardableMarker_evenOnceCompleted() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, GATE);
        q.setDiscardable(true);
        assertTrue(MCQuest.SERIALIZER.deserializeNBT(MCQuest.SERIALIZER.serializeNBT(q)).isDiscardable());
        assertTrue(MCQuest.SERIALIZER.deserializeNBT(MCQuest.SERIALIZER.serializeNBT(q.completed(null))).isDiscardable());
    }

    @Test
    void theQuestCardOffersRemoval_onlyForADiscardableQuest() {
        UUID batch = UUID.randomUUID();
        MCQuest tutorial = MCQuest.standalone(batch, null, GATE);
        MCQuest procedural = MCQuest.standalone(batch, null, GATE);
        procedural.setDiscardable(true);

        assertNull(UIQuest.removableBatch(tutorial));
        assertEquals(batch, UIQuest.removableBatch(procedural));
    }
}
