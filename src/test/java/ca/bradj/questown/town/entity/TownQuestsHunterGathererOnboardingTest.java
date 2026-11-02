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

/** The first-hunter batch is onboarding, so a Block of Progress spend promises the hunter. */
class TownQuestsHunterGathererOnboardingTest {

    @BeforeAll
    static void bootstrap() {
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
        // This batch brings villager 2, so it carries its bed.
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
        // This batch brings villager 2, so it carries its bed.
        TownQuests quests = new TownQuests();
        quests.addHunterGatherer(view());

        MCQuestBatch batch = quests.questBatches.getAllBatches().iterator().next();
        assertTrue(batch.getAll().stream().anyMatch(q -> SpecialQuests.BEDROOM.equals(q.getWantedId())));
    }

    private static TutorialTownView view() {
        return new TutorialTownView() {
            @Override
            public void provisionArrival(
                    ca.bradj.questown.town.quests.MCReward reward,
                    java.util.Collection<net.minecraft.resources.ResourceLocation> wanted,
                    java.util.function.Consumer<net.minecraft.resources.ResourceLocation> addRoomQuest
            ) {
            }
            @Override
            public void revealSkill(ca.bradj.questown.jobs.JobID skill) {
            }
            @Override
            public int villagerCount() {
                return 2;
            }

            @Override
            public boolean anyVillagerOnRoot(String rootId) {
                return false;
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
