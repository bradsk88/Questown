package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.special.SpecialQuests;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-14 playtest ("Had to force hunter job change. Something caused the
 * guaranteed hunter job change to not work. That's a recent regression."): the
 * tutorial's first-hunter batch ({@code addHunterGatherer}) was not marked
 * onboarding, so its open hunter JOB_CHANGE quest was invisible to
 * {@code onboardingJobChangeGuarantee}, and a BOP spend with no BOP blessing
 * pre-rolled arbitrarily (fisher) instead of promising the hunter the quest
 * names.
 */
class TownQuestsHunterGathererOnboardingTest {

    @BeforeAll
    static void bootstrap() {
        // addHunterGatherer touches Items.STONE_SWORD via Compat.getItemId.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void hunterGathererJobChangeQuestIsOnboardingAndFillsTheGuarantee() {
        TownQuests quests = new TownQuests();
        quests.addHunterGatherer(view());

        MCQuestBatch batch = quests.questBatches.getAllBatches().iterator().next();
        MCQuest hunterQuest = batch.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE)
                .findFirst()
                .orElseThrow();
        assertTrue(hunterQuest.isOnboarding(),
                "the first-hunter batch must be onboarding-flagged like the other tutorial batches");
        assertEquals(new JobID("hunter", "sword"),
                quests.onboardingJobChangeGuarantee(VillagerUUID.random()).orElseThrow(),
                "an open first-hunter quest must fill the BOP-spend guarantee slot");
    }

    @Test
    void bedroomQuest_usesTheStandardRoomTooltip() {
        // 2026-10-18 playtest: "The onboarding bed quest seems to have custom text. But the
        // standard 'place these blocks in a room' tooltip would be better." QuestsScreen shows
        // flavor text when present and the room-recipe tooltip otherwise.
        TownQuests quests = new TownQuests();
        quests.addHunterGatherer(view());

        MCQuestBatch batch = quests.questBatches.getAllBatches().iterator().next();
        MCQuest bedroomQuest = batch.getAll().stream()
                .filter(q -> SpecialQuests.BEDROOM.equals(q.getWantedId()))
                .findFirst()
                .orElseThrow();
        assertNull(bedroomQuest.getFlavorText(), "the bedroom quest must not override the room tooltip");
    }

    @Test
    void hunterGathererBatch_carriesTheBedForTheVillagerItBrings() {
        // Onboarding rule (dev, 2026-10-18): a batch that brings a villager carries the bed that
        // villager will sleep in. This batch's reward is villager 2.
        TownQuests quests = new TownQuests();
        quests.addHunterGatherer(view());

        MCQuestBatch batch = quests.questBatches.getAllBatches().iterator().next();
        assertTrue(batch.getAll().stream().anyMatch(q -> SpecialQuests.BEDROOM.equals(q.getWantedId())));
    }

    private static TutorialTownView view() {
        return new TutorialTownView() {
            @Override
            public int villagerCount() {
                return 2;
            }

            @Override
            public boolean tutorialBopGranted() {
                return true;
            }

            @Override
            public void grantBop(int amount) {
            }

            @Override
            public void markTutorialBopGranted() {
            }

            @Override
            public void broadcastMessage(String message) {
            }

            @Override
            public void broadcastTutorialToast(String titleKey, String descriptionKey) {
            }

            @Override
            public void fireTutorialTrigger(TutorialTrigger.Triggers trigger) {
            }

            @Override
            public ResourceLocation getExoticWood() {
                return new ResourceLocation("oak_wood");
            }

            @Override
            public ResourceLocation getRoomForJob(JobID job) {
                return new ResourceLocation("job_room");
            }

            @Override
            public MCReward makeReward(String phaseName) {
                return new NoOpReward();
            }
        };
    }
}
