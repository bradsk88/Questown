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

/**
 * Playtest rulings 2026-10-15 (05:34:56) and 2026-10-17 (04:31:52): "the second villager
 * should ONLY arrive after the raw food quest has been COMPLETED."
 * {@link TownQuests#isArrivalGateOpen(long)} is the gate {@code TownFlagBlockEntity.morningTick}
 * and the "...and one more in the morning" card share; {@link TownQuests#releaseArrivals}
 * is the per-morning split that counts each villager it lets in.
 *
 * The morningTick wiring is registry-bound (not loadable headless) - this suite pins the
 * pure predicate and split it delegates to. The fixture mirrors production: onboarding
 * batches (markOnboarding) + the raw-food LOOT quest posted by {@code addHunterGatherer}.
 */
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
        // Test harnesses and hand-grown towns never post the onboarding chain; the gate
        // must not hold their arrivals hostage.
        assertTrue(new TownQuests().isArrivalGateOpen(3));
    }

    @Test
    void firstVillagerIsNeverHeld() {
        assertTrue(onboardingTown().isArrivalGateOpen(0));
    }

    @Test
    void secondVillagerHeldBeforeTheRawFoodQuestIsPosted() {
        // The 2026-10-17 bug: on the morning after the basics batch no LOOT quest existed
        // yet, the old "no loot quests = satisfied" rule read as open, and both queued
        // arrivals spawned together.
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
        // Exactly the observed morning: the gate's first-villager spawn and the basics
        // kickoff spawn were both queued; counting the first release keeps the second held.
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
