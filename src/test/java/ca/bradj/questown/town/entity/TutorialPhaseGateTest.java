package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.TagsInit;
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

    private TownQuests questsWithDayOneBatches() {
        // The new day-one world (.scratch/mat-gated-arrival): the campfire and
        // gate batches both exist from flag placement and both are done; the
        // arrival + basics batch came from the gate reward.
        TownQuests quests = newTownQuests();
        MCQuestBatch camp = new MCQuestBatch(null, null, new NoOpReward());
        camp.addNewQuest(null, SpecialQuests.CAMPFIRE);
        camp.markRecipeAsComplete(null, SpecialQuests.CAMPFIRE);
        quests.questBatches.add(camp);
        MCQuestBatch gate = new MCQuestBatch(null, null, new NoOpReward());
        gate.addNewQuest(null, SpecialQuests.TOWN_GATE);
        gate.markRecipeAsComplete(null, SpecialQuests.TOWN_GATE);
        quests.questBatches.add(gate);
        return quests;
    }

    /** Seeds the (completed) basics batch the welcome-mat reward posts. */
    private void completeBasics(TownQuests quests) {
        MCQuestBatch basics = new MCQuestBatch(null, null, new NoOpReward());
        basics.addNewQuest(null, SpecialQuests.JOB_BOARD);
        basics.addNewQuest(null, SpecialQuests.STORE_ROOM_SMALL);
        basics.markRecipeAsComplete(null, SpecialQuests.JOB_BOARD);
        basics.markRecipeAsComplete(null, SpecialQuests.STORE_ROOM_SMALL);
        quests.questBatches.add(basics);
    }

    // ===== Day one: campfire + gate batches, no arrival yet =====

    @Test
    void whenDayOneFlag_zeroVillagers_noPhaseApplies() {
        // No tutorial phase keys off the campfire batch any more: the first
        // villager is the gate batch's reward, so with zero villagers the
        // machine must wait (SKIPPED) even when a batch request is pending.
        TownQuests quests = questsWithCampfireOnly();
        TestTutorialTownView view = TestTutorialTownView.builder().build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.SKIPPED, result);
        assertTrue(quests.questBatches.getAll().stream()
                .noneMatch(q -> q.getType() == Quest.QuestType.JOB_CHANGE));
    }

    @Test
    void whenDayOneFlag_twoVillagers_hunterGathererApplies() {
        // A hand-grown town (two villagers already present, no JOB_CHANGE yet)
        // skips straight into the hunter-gatherer batch.
        TownQuests quests = questsWithCampfireOnly();
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(2).build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        // Day-2 playtest: the two sword asks (4 wooden + 1 stone) are consolidated into a single
        // "4 stone swords" item quest - one sword quest only.
        assertTrue(all.stream().anyMatch(q ->
                Compat.getItemId(Items.STONE_SWORD).equals(q.getWantedId())
                        && q.getType() == Quest.QuestType.ITEM
                        && q.getCountNeeded() == 4
        ), "the consolidated sword quest asks for 4 stone swords");
        assertTrue(all.stream().noneMatch(q ->
                Compat.getItemId(Items.WOODEN_SWORD).equals(q.getWantedId())
        ), "the wooden-sword quest is gone (one sword quest only)");
    }

    // ===== Phase 2: day-one done → hunter-gatherer =====

    @Test
    void whenDayOneDoneAndTwoVillagers_hunterGathererAdded() {
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        // The hunter's food quest is a LOOT quest on the raw food tag: it completes on
        // extraction (production), not on a chest deposit.
        assertTrue(all.stream().anyMatch(q ->
                TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location().equals(q.getWantedId())
                        && q.getType() == Quest.QuestType.LOOT
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
    void whenDayOneDoneAndTwoVillagers_bopGranted() {
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        quests.addTutorialBatches(view);

        assertEquals(3, view.getBopGranted());
        assertTrue(view.tutorialBopGranted());
    }

    @Test
    void whenDayOneDoneButOneVillager_noAdvance() {
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(1)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
    }

    @Test
    void whenHunterBatchAlreadyOpen_notReapplied() {
        // The hunter-gatherer batch (with its JOB_CHANGE quest) already exists:
        // the machine must not open a second one just because a second villager
        // arrived while the first job change is pending.
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();
        quests.addTutorialBatches(view);
        int stoneSwordsBefore = (int) quests.questBatches.getAll().stream()
                .filter(q -> Compat.getItemId(Items.STONE_SWORD).equals(q.getWantedId()))
                .count();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.SKIPPED, result);
        int stoneSwordsAfter = (int) quests.questBatches.getAll().stream()
                .filter(q -> Compat.getItemId(Items.STONE_SWORD).equals(q.getWantedId()))
                .count();
        assertEquals(stoneSwordsBefore, stoneSwordsAfter, "no duplicate hunter batch");
    }

    // ===== Phase 2.5: Hunter → Job Change + Food =====

    private TownQuests questsAtPhase2_5() {
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        // Add hunter-gatherer quests
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();
        quests.addTutorialBatches(view);

        // Complete the hunter job change quest to have one JOB_CHANGE
        ResourceLocation hunterJob = JobID.toRL(new JobID("hunter", "sword"));
        quests.questBatches.markRecipeAsComplete(null, hunterJob);
        quests.questBatches.markRecipeAsComplete(null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location());
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.STONE_SWORD));
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.BEDROOM);

        return quests;
    }

    /**
     * Same state as {@link #questsAtPhase2_5()} except the raw-food LOOT quest is left
     * incomplete: no live raw-food producer exists yet, so phase 2.5 must not open.
     */
    private TownQuests questsAfterHunterRawLootOutstanding() {
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();
        quests.addTutorialBatches(view);

        ResourceLocation hunterJob = JobID.toRL(new JobID("hunter", "sword"));
        quests.questBatches.markRecipeAsComplete(null, hunterJob);
        // Raw-food LOOT deliberately left incomplete.
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.STONE_SWORD));
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.BEDROOM);

        return quests;
    }

    @Test
    void whenHunterDoneAndThreeVillagers_jobChangeAndFoodAdded() {
        TownQuests quests = questsAtPhase2_5();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        // Phase 2.5 teaches the cheap cook and then proves it cooks.
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                        && q.getWantedId().equals(JobID.toRL(new JobID("cook", "simple_furnace_food")))
        ));
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.LOOT
                        && q.getWantedId().equals(TagsInit.Items.VILLAGER_FOOD.location())
        ));
        // The batch carries the cook's room quest too: every cook/* job requires
        // kitchen_small (see cook_simple_furnace_food.json), and without it the
        // staffed cook has no selectable work and the batch can never complete.
        ResourceLocation kitchenSmall = new ResourceLocation("questown", "kitchen_small");
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.ROOM
                        && q.getWantedId().equals(kitchenSmall)
        ), "phase 2.5 batch must carry the kitchen_small room quest");
    }

    @Test
    void whenRawFoodLootNotComplete_phase2_5Skipped() {
        TownQuests quests = questsAfterHunterRawLootOutstanding();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        // Phase 2.5 must wait until a raw-food producer has actually produced; the ladder
        // stops here with SKIPPED rather than advancing to later phases.
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
        boolean cookAdded = quests.questBatches.getAll().stream().anyMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                        && q.getWantedId().equals(JobID.toRL(new JobID("cook", "simple_furnace_food")))
        );
        assertFalse(cookAdded, "cook job change should not be added before raw food is produced");
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

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();
        quests.addTutorialBatches(view);   // applies phase 2.5: cook JOB_CHANGE + kitchen ROOM + VILLAGER_FOOD LOOT

        // Complete the second job change (the cook) and the cooked-food LOOT quest.
        // (The kitchen ROOM quest is not needed for the phase gate, which tracks
        // JOB_CHANGE completions; the batch reward does need it.)
        quests.questBatches.markRecipeAsComplete(null, JobID.toRL(new JobID("cook", "simple_furnace_food")));
        quests.questBatches.markRecipeAsComplete(null, TagsInit.Items.VILLAGER_FOOD.location());

        // Two completed job changes open the post-cook miner beat (phase 2.7): post it,
        // complete all four of its quests (JOB_CHANGE + farm room + pickaxes + coal), and
        // run the walk once more so the fixed phases can continue past it.
        quests.addTutorialBatches(view);   // applies phase 2.7: the miner beat
        completeMinerBeat(quests);
        quests.addTutorialBatches(view);   // walk continues past the done miner beat

        return quests;
    }

    /** Completes the post-cook miner beat's quests (room = the view's farm stand-in). */
    private void completeMinerBeat(TownQuests quests) {
        quests.questBatches.markRecipeAsComplete(null, JobID.toRL(new JobID("miner", "coal")));
        quests.questBatches.markRecipeAsComplete(null, new ResourceLocation("questown", "farm"));
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.WOODEN_PICKAXE));
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.COAL));
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

        // Only the hunter job change is done, the cook job change is not added yet.
        // We need 3 villagers for phase 2.5 to proceed past the villager check.
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        // This should add the cook job change quest (phase 2.5), not advance to phase 4.
        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.APPLIED, result);
        // The count of JOB_CHANGE quests should be 2 now (hunter + cook).
        long jobChangeCount = quests.questBatches.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.JOB_CHANGE)
                .count();
        assertEquals(2, jobChangeCount);
    }

    @Test
    void whenTwoJobChangesDoneButNotComplete_noAdvance() {
        TownQuests quests = questsAtPhase2_5();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();
        quests.addTutorialBatches(view);

        // Don't complete the second job change: only one job change is done, so the
        // post-cook miner beat (which needs two) is not even posted.
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
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

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
                TutorialTownView.PHASE_MINER,
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
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        quests.addTutorialBatches(view);

        assertFalse(view.getMessagesSent().isEmpty());
        assertTrue(view.getMessagesSent().get(0).contains("New quests"));
    }

    // ===== Milestone: procedural-batch counting =====

    @Test
    void campfireBatch_isNotProceduralBatch() {
        // Regression: placing a campfire (the very first step) was instantly counted as a
        // procedural-batch milestone (the "village is thriving" first-batch broadcast, since
        // removed 2026-10-15, and the 5/10/20 chapter triggers) because campfire batch
        // completion was counted as a procedural batch.
        MCQuestBatch campfireBatch = new MCQuestBatch(null, null, new NoOpReward());
        campfireBatch.addNewQuest(null, SpecialQuests.CAMPFIRE);
        assertFalse(TownQuests.isProceduralBatch(campfireBatch));
    }

    // ===== Superset completion for onboarding quests =====

    private static final ResourceLocation STORE_ROOM_MEDIUM = new ResourceLocation("questown", "store_room_medium");
    private static final ResourceLocation CRAFTING_ROOM = new ResourceLocation("questown", "crafting_room");

    /**
     * Drives the onboarding superset completion path the same way the production wiring would:
     * {@code roomRecipeCreated} matches a room's recipe IDs against each onboarding quest's wanted id
     * and calls {@code markQuestAsComplete} for any match. This tests the mechanism (a superset
     * matches a smaller storeroom quest) without a live world, which the full method needs.
     */
    private static void applySupersetCompletion(
            TownQuests quests,
            ResourceLocation... matchedRecipeIds
    ) {
        for (Quest quest : quests.getAll()) {
            if (quest.isOnboarding() && !quest.isComplete()) {
                ResourceLocation wanted = (ResourceLocation) quest.getWantedId();
                if (Set.of(matchedRecipeIds).contains(wanted)) {
                    quests.markQuestAsComplete(null, wanted);
                }
            }
        }
    }

    private static boolean isComplete(
            TownQuests quests,
            ResourceLocation wanted
    ) {
        return quests.questBatches.getAll().stream()
                .filter(q -> wanted.equals(q.getWantedId()))
                .anyMatch(Quest::isComplete);
    }

    /** Adds a single quest to a fresh TownQuests via the onboarding-style Inputs flow. */
    private TownQuests questsWithQuest(MCQuest q) {
        TownQuests quests = newTownQuests();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(q.getBatchUUID(), null);
        inputs.addNewQuest(q);
        quests.questBatches.add(inputs.withRewardUponCompletion(new NoOpReward()));
        return quests;
    }

    @Test
    void superset_onboardingSmallStoreroomQuest_completedByHugeStoreroom() {
        MCQuest smallQuest = MCQuest.standalone(null, null, SpecialQuests.STORE_ROOM_SMALL);
        smallQuest.setOnboarding(true);
        TownQuests quests = questsWithQuest(smallQuest);

        // A huge storeroom (many chests) matches small/medium/big/huge at once.
        applySupersetCompletion(quests,
                SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM,
                new ResourceLocation("questown", "store_room_big"),
                new ResourceLocation("questown", "store_room_huge"));

        assertTrue(isComplete(quests, SpecialQuests.STORE_ROOM_SMALL));
    }

    @Test
    void superset_randomSmallStoreroomQuest_notCompletedByHugeStoreroom() {
        MCQuest randomSmallQuest = MCQuest.standalone(null, null, SpecialQuests.STORE_ROOM_SMALL);
        // not onboarding
        TownQuests quests = questsWithQuest(randomSmallQuest);

        applySupersetCompletion(quests,
                SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM,
                new ResourceLocation("questown", "store_room_big"),
                new ResourceLocation("questown", "store_room_huge"));

        assertFalse(isComplete(quests, SpecialQuests.STORE_ROOM_SMALL));
    }

    @Test
    void superset_onboardingMediumUpgradeQuest_completedByHugeStoreroom() {
        MCQuest upgradeQuest = MCQuest.upgrade(null, null, SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM);
        upgradeQuest.setOnboarding(true);
        TownQuests quests = questsWithQuest(upgradeQuest);

        applySupersetCompletion(quests,
                SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM,
                new ResourceLocation("questown", "store_room_big"),
                new ResourceLocation("questown", "store_room_huge"));

        assertTrue(isComplete(quests, STORE_ROOM_MEDIUM));
    }

    @Test
    void superset_unmatchedOnboardingQuest_notCompleted() {
        MCQuest craftingQuest = MCQuest.standalone(null, null, CRAFTING_ROOM);
        craftingQuest.setOnboarding(true);
        TownQuests quests = questsWithQuest(craftingQuest);

        // The room is a storeroom; it does not match the crafting room recipe.
        applySupersetCompletion(quests,
                SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM,
                new ResourceLocation("questown", "store_room_big"),
                new ResourceLocation("questown", "store_room_huge"));

        assertFalse(isComplete(quests, CRAFTING_ROOM));
    }

}
