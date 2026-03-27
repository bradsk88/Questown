package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatch;
import ca.bradj.questown.town.special.SpecialQuests;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TutorialPhaseGateTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final QuestBatch.ChangeListener<MCQuest> NO_OP_LISTENER = new QuestBatch.ChangeListener<>() {
        @Override
        public void questCompleted(MCQuest quest) {}

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {}

        @Override
        public void questLost(MCQuest quest) {}
    };

    private TownQuests newTownQuests() {
        TownQuests quests = new TownQuests();
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        return quests;
    }

    private TownQuests questsWithCampfireOnly() {
        TownQuests quests = newTownQuests();
        MCQuestBatch batch = new MCQuestBatch(null, null, new NoOpReward());
        batch.addNewQuest(null, SpecialQuests.CAMPFIRE);
        quests.questBatches.add(batch);
        return quests;
    }

    private TownQuests questsWithCampfireDone() {
        TownQuests quests = newTownQuests();
        MCQuestBatch batch = new MCQuestBatch(null, null, new NoOpReward());
        batch.addNewQuest(null, SpecialQuests.CAMPFIRE);
        batch.markRecipeAsComplete(null, SpecialQuests.CAMPFIRE);
        quests.questBatches.add(batch);
        return quests;
    }

    private void addKickoffQuests(TownQuests quests) {
        TestTutorialTownView view = TestTutorialTownView.builder().build();
        quests.addTutorialBatches(view);
    }

    private void completeAllKickoffQuests(TownQuests quests) {
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.TOWN_GATE);
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.JOB_BOARD);
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.STORE_ROOM_SMALL);
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.WOODEN_SWORD));
    }

    // ===== Phase 1: Campfire → Kickoff =====

    @Test
    void whenOnlyCampfireQuest_kickoffQuestsAdded() {
        TownQuests quests = questsWithCampfireOnly();
        TestTutorialTownView view = TestTutorialTownView.builder().build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        assertTrue(all.stream().anyMatch(q -> SpecialQuests.TOWN_GATE.equals(q.getWantedId())));
        assertTrue(all.stream().anyMatch(q -> SpecialQuests.JOB_BOARD.equals(q.getWantedId())));
        assertTrue(all.stream().anyMatch(q -> SpecialQuests.STORE_ROOM_SMALL.equals(q.getWantedId())));
        assertTrue(all.stream().anyMatch(q ->
                Compat.getItemId(Items.WOODEN_SWORD).equals(q.getWantedId())
                && q.getType() == Quest.QuestType.ITEM
        ));
    }

    @Test
    void whenCampfireNotDone_noAdvance() {
        TownQuests quests = questsWithCampfireOnly();
        TestTutorialTownView view = TestTutorialTownView.builder().build();

        // First call adds kickoff quests from campfire
        quests.addTutorialBatches(view);

        // Campfire quest is still there but kickoff quests are not all done
        // The method should skip on subsequent call since batches > 1 and kickoff not done
        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    // ===== Phase 2: Kickoff → Hunter-Gatherer =====

    @Test
    void whenKickoffDoneAndTwoVillagers_hunterGathererAdded() {
        TownQuests quests = questsWithCampfireDone();
        addKickoffQuests(quests);
        completeAllKickoffQuests(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        assertTrue(all.stream().anyMatch(q ->
                Compat.getItemId(Items.MUTTON).equals(q.getWantedId())
        ));
        assertTrue(all.stream().anyMatch(q ->
                Compat.getItemId(Items.STONE_SWORD).equals(q.getWantedId())
        ));
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                && q.getWantedId().equals(JobID.toRL(new JobID("hunter", "sword")))
        ));
        assertTrue(all.stream().anyMatch(q ->
                SpecialQuests.BEDROOM.equals(q.getWantedId())
        ));
    }

    @Test
    void whenKickoffDoneAndTwoVillagers_bopGranted() {
        TownQuests quests = questsWithCampfireDone();
        addKickoffQuests(quests);
        completeAllKickoffQuests(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        quests.addTutorialBatches(view);

        assertEquals(3, view.getBopGranted());
        assertTrue(view.tutorialBopGranted());
    }

    @Test
    void whenKickoffDoneButOneVillager_noAdvance() {
        TownQuests quests = questsWithCampfireDone();
        addKickoffQuests(quests);
        completeAllKickoffQuests(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(1)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    @Test
    void whenKickoffPartiallyDone_noAdvance() {
        TownQuests quests = questsWithCampfireDone();
        addKickoffQuests(quests);
        // Only complete some kickoff quests
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.TOWN_GATE);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    // ===== Phase 2.5: Hunter → Job Change + Food =====

    private TownQuests questsAtPhase2_5() {
        TownQuests quests = questsWithCampfireDone();
        addKickoffQuests(quests);
        completeAllKickoffQuests(quests);

        // Add hunter-gatherer quests
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();
        quests.addTutorialBatches(view);

        // Complete the hunter job change quest to have one JOB_CHANGE
        ResourceLocation hunterJob = JobID.toRL(new JobID("hunter", "sword"));
        quests.questBatches.markRecipeAsComplete(null, hunterJob);
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.MUTTON));
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.STONE_SWORD));
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.BEDROOM);

        return quests;
    }

    @Test
    void whenHunterDoneAndThreeVillagers_jobChangeAndFoodAdded() {
        TownQuests quests = questsAtPhase2_5();

        JobID farmerJob = new JobID("farmer", "wheat");
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .jobForTutorial(farmerJob)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        assertTrue(all.stream().anyMatch(q ->
                Compat.getItemId(Items.APPLE).equals(q.getWantedId())
                && q.getType() == Quest.QuestType.ITEM
        ));
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                && q.getWantedId().equals(JobID.toRL(farmerJob))
        ));
    }

    @Test
    void whenHunterDoneButTwoVillagers_noAdvance() {
        TownQuests quests = questsAtPhase2_5();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    // ===== Phase 2.5 → Phase 4 (Phase 3 is skipped when tutorial quests have null owners) =====

    private TownQuests questsWithTwoJobChangesDone() {
        TownQuests quests = questsAtPhase2_5();

        JobID farmerJob = new JobID("farmer", "wheat");
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .jobForTutorial(farmerJob)
                .build();
        quests.addTutorialBatches(view);

        // Complete the second job change and apple quest
        quests.questBatches.markRecipeAsComplete(null, JobID.toRL(farmerJob));
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.APPLE));

        return quests;
    }

    @Test
    void whenTwoJobChangesDone_crafterQuestsAdded_tutorialCompleteTriggerFired() {
        TownQuests quests = questsWithTwoJobChangesDone();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        ResourceLocation crafterStick = JobID.toRL(new JobID("crafter", "stick"));
        ResourceLocation crafterBowl = JobID.toRL(new JobID("crafter", "bowl"));
        ResourceLocation craftingRoom = new ResourceLocation("questown", "crafting_room");

        assertTrue(all.stream().anyMatch(q ->
                crafterStick.equals(q.getWantedId()) && q.getType() == Quest.QuestType.JOB_CHANGE
        ));
        assertTrue(all.stream().anyMatch(q ->
                craftingRoom.equals(q.getWantedId()) && q.getType() == Quest.QuestType.ROOM
        ));
        assertTrue(all.stream().anyMatch(q ->
                crafterBowl.equals(q.getWantedId()) && q.getType() == Quest.QuestType.JOB_CHANGE
        ));
        assertTrue(view.getTriggersFired().contains(TutorialTrigger.Triggers.TutorialComplete));
    }

    @Test
    void whenOneJobChangeDone_noAdvance() {
        TownQuests quests = questsAtPhase2_5();

        // Only the hunter job change is done, second one not added yet
        // But we need 3 villagers for it to proceed past the villager check
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .jobForTutorial(new JobID("farmer", "wheat"))
                .build();

        // This should add the second job change quest (phase 2.5), not advance to phase 4
        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.APPLIED, result);
        // The count of JOB_CHANGE quests should be 2 now (hunter + farmer)
        long jobChangeCount = quests.questBatches.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE)
                .count();
        assertEquals(2, jobChangeCount);
    }

    @Test
    void whenTwoJobChangesDoneButNotComplete_noAdvance() {
        TownQuests quests = questsAtPhase2_5();

        JobID farmerJob = new JobID("farmer", "wheat");
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .jobForTutorial(farmerJob)
                .build();
        quests.addTutorialBatches(view);

        // Don't complete second job change
        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    // ===== Phase 5: Crafter Bowl → Supply Chain =====

    private TownQuests questsWithCrafterDone() {
        TownQuests quests = questsWithTwoJobChangesDone();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();
        quests.addTutorialBatches(view);

        // Complete crafter quests
        ResourceLocation crafterStick = JobID.toRL(new JobID("crafter", "stick"));
        ResourceLocation crafterBowl = JobID.toRL(new JobID("crafter", "bowl"));
        ResourceLocation craftingRoom = new ResourceLocation("questown", "crafting_room");
        quests.questBatches.markRecipeAsComplete(null, crafterStick);
        quests.questBatches.markRecipeAsComplete(null, craftingRoom);
        quests.questBatches.markRecipeAsComplete(null, crafterBowl);

        return quests;
    }

    @Test
    void whenBowlJobDone_supplyChainAdded() {
        TownQuests quests = questsWithCrafterDone();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        assertTrue(all.stream().anyMatch(q -> q.getType() == Quest.QuestType.CONCURRENT_JOBS));
        ResourceLocation kitchenSmall = new ResourceLocation("questown", "kitchen_small");
        assertTrue(all.stream().anyMatch(q ->
                kitchenSmall.equals(q.getWantedId()) && q.getType() == Quest.QuestType.ROOM
        ));
    }

    @Test
    void whenBowlJobNotDone_noAdvance() {
        TownQuests quests = questsWithTwoJobChangesDone();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();
        quests.addTutorialBatches(view);

        // Complete only crafter stick, not bowl
        ResourceLocation crafterStick = JobID.toRL(new JobID("crafter", "stick"));
        ResourceLocation craftingRoom = new ResourceLocation("questown", "crafting_room");
        quests.questBatches.markRecipeAsComplete(null, crafterStick);
        quests.questBatches.markRecipeAsComplete(null, craftingRoom);

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    // ===== Phase 6: Supply Chain → Storage Upgrade =====

    private TownQuests questsAtPhase6() {
        TownQuests quests = questsWithCrafterDone();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();
        quests.addTutorialBatches(view);

        // Complete supply chain quests
        ResourceLocation concurrentJobs = new ResourceLocation("questown", "concurrent_jobs");
        ResourceLocation kitchenSmall = new ResourceLocation("questown", "kitchen_small");
        quests.questBatches.markRecipeAsComplete(null, concurrentJobs);
        quests.questBatches.markRecipeAsComplete(null, kitchenSmall);

        return quests;
    }

    @Test
    void whenConcurrentJobsDone_storageUpgradeAdded_secondJobTypeTriggerFired() {
        TownQuests quests = questsAtPhase6();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        ResourceLocation storeRoomMedium = new ResourceLocation("questown", "store_room_medium");
        assertTrue(all.stream().anyMatch(q ->
                storeRoomMedium.equals(q.getWantedId()) && q.getType() == Quest.QuestType.ROOM
        ));
        assertTrue(view.getTriggersFired().contains(TutorialTrigger.Triggers.SecondJobType));
    }

    // ===== Phase 7: Storage → Exotic Wood =====

    private TownQuests questsAtPhase7() {
        TownQuests quests = questsAtPhase6();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();
        quests.addTutorialBatches(view);

        // Complete storage upgrade
        ResourceLocation storeRoomMedium = new ResourceLocation("questown", "store_room_medium");
        quests.questBatches.markRecipeAsComplete(null, storeRoomMedium);

        return quests;
    }

    @Test
    void whenStorageUpgradeDone_exoticWoodAdded_firstRoomUpgradeTriggerFired() {
        TownQuests quests = questsAtPhase7();

        ResourceLocation exoticWood = new ResourceLocation("minecraft", "jungle_log");
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .exoticWood(exoticWood)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        assertTrue(all.stream().anyMatch(q ->
                exoticWood.equals(q.getWantedId())
                && q.getType() == Quest.QuestType.ITEM
                && q.getFlavorText() != null
                && q.getFlavorText().contains("flagpole")
        ));
        assertTrue(view.getTriggersFired().contains(TutorialTrigger.Triggers.FirstRoomUpgrade));
    }

    // ===== Completion =====

    @Test
    void whenAllPhasesDone_returnsDone() {
        TownQuests quests = questsAtPhase7();

        ResourceLocation exoticWood = new ResourceLocation("minecraft", "jungle_log");
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .exoticWood(exoticWood)
                .build();
        quests.addTutorialBatches(view);

        // Complete exotic wood quest
        quests.questBatches.markRecipeAsComplete(null, exoticWood);

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.DONE, result);
    }

    // ===== Edge cases =====

    @Test
    void whenEmpty_returnsSkipped() {
        TownQuests quests = new TownQuests();
        TestTutorialTownView view = TestTutorialTownView.builder().build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    @Test
    void whenBopAlreadyGranted_noDuplicateGrant() {
        TownQuests quests = questsWithCampfireDone();
        addKickoffQuests(quests);
        completeAllKickoffQuests(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .tutorialBopGranted(true)
                .build();

        quests.addTutorialBatches(view);
        assertEquals(0, view.getBopGranted());
    }

    @Test
    void allPhaseNamesAreFromKnownConstants() {
        Set<String> knownPhaseNames = Set.of(
                TutorialTownView.PHASE_KICKOFF,
                TutorialTownView.PHASE_HUNTER_GATHERER,
                TutorialTownView.PHASE_JOB_CHANGE_FOOD,
                TutorialTownView.PHASE_NEW_JOB_ROOM,
                TutorialTownView.PHASE_4A,
                TutorialTownView.PHASE_4B,
                TutorialTownView.PHASE_5,
                TutorialTownView.PHASE_6,
                TutorialTownView.PHASE_7
        );

        // Run through all phases to completion and collect phase names
        TownQuests quests = questsAtPhase7();
        ResourceLocation exoticWood = new ResourceLocation("minecraft", "jungle_log");
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .exoticWood(exoticWood)
                .build();
        quests.addTutorialBatches(view);
        quests.questBatches.markRecipeAsComplete(null, exoticWood);
        quests.addTutorialBatches(view);

        List<String> collectedNames = view.getRewardPhaseNames();
        assertFalse(collectedNames.isEmpty(), "Should have collected at least one phase name");
        for (String name : collectedNames) {
            assertTrue(knownPhaseNames.contains(name),
                    "Phase name '" + name + "' is not in the known constants set");
        }
    }

    @Test
    void whenNewQuestsAdded_broadcastSent() {
        TownQuests quests = questsWithCampfireDone();
        addKickoffQuests(quests);
        completeAllKickoffQuests(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        quests.addTutorialBatches(view);

        assertFalse(view.getMessagesSent().isEmpty());
        assertTrue(view.getMessagesSent().get(0).contains("New quests"));
    }

    // ===== Milestone: "Your village is thriving" =====

    @Test
    void campfireBatch_isNotProceduralBatch() {
        // Regression: placing a campfire (the very first step) was instantly
        // broadcasting "your village is thriving" because campfire batch
        // completion was counted as a procedural batch milestone.
        MCQuestBatch campfireBatch = new MCQuestBatch(null, null, new NoOpReward());
        campfireBatch.addNewQuest(null, SpecialQuests.CAMPFIRE);
        assertFalse(TownQuests.isProceduralBatch(campfireBatch));
    }
}
