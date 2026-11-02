package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.special.SpecialQuests;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The second villager arrives only once the raw-food quest is done: {@link TownQuests#isArrivalGateOpen(long)} and {@link TownQuests#releaseArrivals}. */
class TownQuestsRawFoodGateTest {

    private static ResourceLocation rawFood() {
        return TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location();
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nonOnboardingTownIsNeverHeld() {
        // A town that never ran onboarding never holds arrivals.
        assertTrue(new TownQuests().isArrivalGateOpen(3));
    }

    @Test
    void firstVillagerIsNeverHeld() {
        assertTrue(onboardingTown().isArrivalGateOpen(0));
    }

    @Test
    void secondVillagerHeldBeforeTheRawFoodQuestIsPosted() {
        // Before the raw-food quest exists, the gate is closed.
        assertFalse(onboardingTown().isArrivalGateOpen(1));
    }

    @Test
    void secondVillagerHeldWhileTheRawFoodQuestIsOpen() {
        TownQuests quests = onboardingTown();
        quests.addBatch(batchWith(MCQuest.loot(UUID.randomUUID(), null, rawFood(), 1)));
        assertFalse(quests.isArrivalGateOpen(1));
    }

    @Test
    void completedRawFoodQuestReleasesTheGate() {
        TownQuests quests = onboardingTown();
        quests.addBatch(batchWith(MCQuest.loot(UUID.randomUUID(), null, rawFood(), 1).completed(null)));
        assertTrue(quests.isArrivalGateOpen(1));
    }

    @Test
    void twoQueuedArrivalsOnAnEmptyTown_onlyTheFirstIsReleased() {
        // Two arrivals queued: one is released, the other held.
        TownQuests.ArrivalRelease<String> r = onboardingTown().releaseArrivals(
                List.of("spawnA", "spawnB"), s -> s.startsWith("spawn"), 0
        );
        assertEquals(List.of("spawnA"), r.arrivals());
        assertEquals(List.of("spawnB"), r.held());
    }

    @Test
    void nonArrivalRewardsAreNeverHeld() {
        TownQuests.ArrivalRelease<String> r = onboardingTown().releaseArrivals(
                List.of("spawnA", "postQuest"), s -> s.startsWith("spawn"), 1
        );
        assertEquals(List.of("postQuest"), r.others());
        assertEquals(List.of(), r.arrivals());
        assertEquals(List.of("spawnA"), r.held());
    }

    private static TownQuests onboardingTown() {
        TownQuests quests = new TownQuests();
        MCQuest campfire = MCQuest.standalone(UUID.randomUUID(), null, SpecialQuests.CAMPFIRE).completed(null);
        campfire.setOnboarding(true);
        quests.addBatch(batchWith(campfire));
        return quests;
    }

    private static MCQuestBatch batchWith(MCQuest q) {
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(UUID.randomUUID(), null);
        inputs.addNewQuest(q);
        return inputs.withRewardUponCompletion(new NoOpReward());
    }
}
