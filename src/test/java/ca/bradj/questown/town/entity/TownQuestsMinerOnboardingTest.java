package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The miner lesson after the cook: the mineshaft, pickaxes, some coal, and a job change to miner. */
class TownQuestsMinerOnboardingTest {

    private static final ResourceLocation MINE_ROOM = new ResourceLocation("block_room/block.questown.mineshaft");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void minerBatchIsOnboardingAndFillsTheGuarantee() {
        TownQuests quests = new TownQuests();
        quests.addMiner(view());

        MCQuestBatch batch = quests.questBatches.getAllBatches().iterator().next();
        MCQuest minerQuest = batch.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE)
                .findFirst()
                .orElseThrow();
        assertTrue(minerQuest.isOnboarding(),
                "the post-cook miner batch must be onboarding-flagged like the other tutorial batches");
        assertEquals(new JobID("miner", "coal"),
                quests.onboardingJobChangeGuarantee(VillagerUUID.random()).orElseThrow(),
                "an open post-cook miner quest must fill the BOP-spend guarantee slot");
    }

    @Test
    void minerBatchNamesTheRoomThePickaxesAndTheCoal() {
        TownQuests quests = new TownQuests();
        quests.addMiner(view());

        MCQuestBatch batch = quests.questBatches.getAllBatches().iterator().next();

        MCQuest roomQuest = batch.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.ROOM)
                .findFirst()
                .orElseThrow();
        assertEquals(MINE_ROOM, roomQuest.getWantedId());

        assertTrue(batch.getAll().stream().anyMatch(q -> q.getType() == Quest.QuestType.ITEM
                && Compat.getItemId(Items.WOODEN_PICKAXE).equals(q.getWantedId())
                && q.getCountNeeded() == 4));

        // A LOOT quest for coal, counted as the miner brings it back.
        assertTrue(batch.getAll().stream().anyMatch(q -> q.getType() == Quest.QuestType.LOOT
                && Compat.getItemId(Items.COAL).equals(q.getWantedId())
                && q.getCountNeeded() == 3));
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
                return 4;
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
                assertEquals(new JobID("miner", "coal"), job, "the miner beat asks for the miner's own room");
                return MINE_ROOM;
            }

            @Override
            public MCReward makeReward(String phaseName) {
                return new NoOpReward();
            }
        };
    }
}
