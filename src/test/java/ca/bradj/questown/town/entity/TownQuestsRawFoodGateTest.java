package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-15 playtest ruling (user chat 05:34:56): "hold the 2nd villager until
 * the raw food quest is completed." {@link TownQuests#isRawFoodSatisfied()} is
 * the gate {@code TownFlagBlockEntity.morningTick} uses to roll a queued
 * villager spawn into the next morning, and the flag menu uses it to decide
 * when the "...and one more in the morning" card is a true promise.
 *
 * The morningTick gate and the visitor's door pathfinding preference are
 * registry-bound (not loadable headless) - this suite pins their shared pure
 * predicate instead. The fixture mirrors the production raw-food LOOT quest
 * ({@code MCQuest.loot} + batch, as posted by {@code addHunterGatherer}).
 */
class TownQuestsRawFoodGateTest {

    private static final ResourceLocation FOOD = new ResourceLocation("questown", "villager_food");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void noLootQuestsReadsAsSatisfied() {
        // Test harnesses and pre-onboarding flags never post the loot quest;
        // the gate must not hold arrivals hostage in those worlds.
        assertTrue(new TownQuests().isRawFoodSatisfied());
    }

    @Test
    void incompleteLootQuestHoldsTheGate() {
        TownQuests quests = new TownQuests();
        quests.addBatch(batchWith(MCQuest.loot(UUID.randomUUID(), null, FOOD, 1)));
        assertFalse(quests.isRawFoodSatisfied(),
                "an open raw-food quest must hold the second-villager arrival");
    }

    @Test
    void completedLootQuestReleasesTheGate() {
        TownQuests quests = new TownQuests();
        quests.addBatch(batchWith(MCQuest.loot(UUID.randomUUID(), null, FOOD, 1).completed(null)));
        assertTrue(quests.isRawFoodSatisfied());
    }

    private static MCQuestBatch batchWith(MCQuest q) {
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(UUID.randomUUID(), null);
        inputs.addNewQuest(q);
        return inputs.withRewardUponCompletion(new NoOpReward());
    }
}
