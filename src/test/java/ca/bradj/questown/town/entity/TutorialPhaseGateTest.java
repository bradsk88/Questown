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
        // Day one done: the campfire and gate batches, plus the basics batch from the gate reward.
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

    private void completeBasics(TownQuests quests) {
        MCQuestBatch basics = new MCQuestBatch(null, null, new NoOpReward());
        basics.addNewQuest(null, SpecialQuests.JOB_BOARD);
        basics.addNewQuest(null, SpecialQuests.STORE_ROOM_SMALL);
        basics.markRecipeAsComplete(null, SpecialQuests.JOB_BOARD);
        basics.markRecipeAsComplete(null, SpecialQuests.STORE_ROOM_SMALL);
        quests.questBatches.add(basics);
    }

    @Test
    void whenDayOneFlag_zeroVillagers_noPhaseApplies() {
        // With no villagers, the tutorial waits.
        TownQuests quests = questsWithCampfireOnly();
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(0).build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.SKIPPED, result);
        assertTrue(quests.questBatches.getAll().stream()
                .noneMatch(q -> q.getType() == Quest.QuestType.JOB_CHANGE));
    }

    @Test
    void whenDayOneFlag_twoVillagers_hunterGathererApplies() {
        // A town that already has two villagers goes straight to the hunter-gatherer batch.
        TownQuests quests = questsWithCampfireOnly();
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(2).build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        assertTrue(all.stream().anyMatch(q ->
                Compat.getItemId(Items.STONE_SWORD).equals(q.getWantedId())
                        && q.getType() == Quest.QuestType.ITEM
                        && q.getCountNeeded() == 4
        ), "the consolidated sword quest asks for 4 stone swords");
        assertTrue(all.stream().noneMatch(q ->
                Compat.getItemId(Items.WOODEN_SWORD).equals(q.getWantedId())
        ), "the wooden-sword quest is gone (one sword quest only)");
    }

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
        // The hunter's food quest is a LOOT quest on raw food.
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
    void whenDayOneDoneAndOneVillager_hunterGathererAdded() {
        // The batch opens with the first villager alone; the second waits for the raw food.
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(1)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.APPLIED, result);
        assertTrue(quests.questBatches.getAll().stream().anyMatch(q ->
                TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location().equals(q.getWantedId())
                        && q.getType() == Quest.QuestType.LOOT
        ), "the raw-food quest is posted for the first villager");
    }

    @Test
    void whenHunterBatchAlreadyOpen_notReapplied() {
        // No second hunter-gatherer batch while the first job change is pending.
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

        quests.addTutorialBatches(view);
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.BEDROOM);

        return quests;
    }

    private TownQuests questsAfterHunterGathererDone() {
        return questsAfterHunterGathererDone(false);
    }

    /** @param withDayOneEmptyRoom include the first day's empty-room batch, as a real world has */
    private TownQuests questsAfterHunterGathererDone(boolean withDayOneEmptyRoom) {
        TownQuests quests = questsWithDayOneBatches();
        if (withDayOneEmptyRoom) {
            MCQuestBatch dayOne = new MCQuestBatch(null, null, new NoOpReward());
            dayOne.addNewQuest(null, SpecialQuests.EMPTY_ROOM);
            dayOne.markRecipeAsComplete(null, SpecialQuests.EMPTY_ROOM);
            quests.questBatches.add(dayOne);
        }
        completeBasics(quests);
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();
        quests.addTutorialBatches(view);
        quests.questBatches.markRecipeAsComplete(null, JobID.toRL(new JobID("hunter", "sword")));
        quests.questBatches.markRecipeAsComplete(null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location());
        quests.questBatches.markRecipeAsComplete(null, Compat.getItemId(Items.STONE_SWORD));
        quests.questBatches.markRecipeAsComplete(null, SpecialQuests.BEDROOM);
        return quests;
    }

    @Test
    void whenHunterGathererDone_newcomerBedroomBatchAdded_notTheCookBatch() {
        TownQuests quests = questsAfterHunterGathererDone();
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(2).build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        List<MCQuest> all = quests.questBatches.getAll();
        assertEquals(1, all.stream().filter(q -> SpecialQuests.BEDROOM.equals(q.getWantedId()) && !q.isComplete()).count(),
                "one new, open bedroom quest");
        assertTrue(all.stream().noneMatch(q -> SpecialQuests.EMPTY_ROOM.equals(q.getWantedId())));
        assertTrue(all.stream().noneMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                        && q.getWantedId().equals(JobID.toRL(TownQuests.ONBOARDING_COOK_JOB))
        ), "the cook batch waits for the bedroom");
    }

    @Test
    void dayOneEmptyRoomBatch_isNotMistakenForThePostHunterOne() {
        // The first day's empty-room batch is not the post-hunter batch.
        TownQuests quests = questsAfterHunterGathererDone(true);
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(2).build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.APPLIED, result);
        assertEquals(1, quests.questBatches.getAll().stream()
                .filter(q -> SpecialQuests.BEDROOM.equals(q.getWantedId()) && !q.isComplete()).count(),
                "one new, open bedroom quest");
        assertTrue(quests.questBatches.getAll().stream().noneMatch(q ->
                q.getWantedId().equals(JobID.toRL(TownQuests.ONBOARDING_COOK_JOB))
        ), "the cook batch waits for the new room");
    }

    @Test
    void whenEmptyRoomsOpen_cookBatchWaits() {
        TownQuests quests = questsAfterHunterGathererDone();
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(2).build();
        quests.addTutorialBatches(view);

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.SKIPPED, result);
        assertTrue(quests.questBatches.getAll().stream().noneMatch(q ->
                q.getWantedId().equals(JobID.toRL(TownQuests.ONBOARDING_COOK_JOB))
        ));
    }

    @Test
    void emptyRoomQuest_needsARoomNoCompletedQuestClaimed() {
        TownQuests quests = newTownQuests();
        MCQuestBatch batch = new MCQuestBatch(null, null, new NoOpReward());
        batch.addNewQuest(null, SpecialQuests.EMPTY_ROOM);
        batch.addNewQuest(null, SpecialQuests.EMPTY_ROOM);
        quests.questBatches.add(batch);
        MCRoom a = room(0, 0);
        MCRoom b = room(10, 0);

        quests.markQuestAsComplete(a, SpecialQuests.EMPTY_ROOM);
        quests.markQuestAsComplete(a, SpecialQuests.EMPTY_ROOM);
        assertEquals(1, quests.questBatches.getAll().stream().filter(Quest::isComplete).count(),
                "the same room cannot complete both empty-room quests");

        quests.markQuestAsComplete(b, SpecialQuests.EMPTY_ROOM);
        assertEquals(2, quests.questBatches.getAll().stream().filter(Quest::isComplete).count(),
                "a second, new room completes the second quest");
    }

    @Test
    void bedroomQuest_needsABedroomNoBedroomQuestClaimed() {
        // The new villager's bedroom quest doesn't close on the existing bedroom.
        TownQuests quests = newTownQuests();
        MCQuestBatch batch = new MCQuestBatch(null, null, new NoOpReward());
        batch.addNewQuest(null, SpecialQuests.BEDROOM);
        batch.addNewQuest(null, SpecialQuests.BEDROOM);
        quests.questBatches.add(batch);
        MCRoom a = room(0, 0);
        MCRoom b = room(10, 0);

        quests.markQuestAsComplete(a, SpecialQuests.BEDROOM);
        quests.markQuestAsComplete(a, SpecialQuests.BEDROOM);
        assertEquals(1, quests.questBatches.getAll().stream().filter(Quest::isComplete).count(),
                "the same bedroom cannot complete both bedroom quests");

        quests.markQuestAsComplete(b, SpecialQuests.BEDROOM);
        assertEquals(2, quests.questBatches.getAll().stream().filter(Quest::isComplete).count(),
                "a second, new bedroom completes the second quest");
    }

    @Test
    void roomQuest_mayCompleteOnARoomClaimedForAnotherRecipe() {
        TownQuests quests = newTownQuests();
        MCQuestBatch batch = new MCQuestBatch(null, null, new NoOpReward());
        batch.addNewQuest(null, SpecialQuests.EMPTY_ROOM);
        batch.addNewQuest(null, SpecialQuests.STORE_ROOM_SMALL);
        quests.questBatches.add(batch);
        MCRoom a = room(0, 0);

        quests.markQuestAsComplete(a, SpecialQuests.EMPTY_ROOM);
        quests.markQuestAsComplete(a, SpecialQuests.STORE_ROOM_SMALL);

        assertEquals(2, quests.questBatches.getAll().stream().filter(Quest::isComplete).count());
    }

    private static MCRoom room(int x, int z) {
        return new MCRoom(
                new Position(x, z),
                ImmutableList.of(InclusiveSpace.from(x, z).to(x + 3, z + 3)),
                64
        );
    }

    /** As at phase 2.5, but with the raw-food quest still open. */
    private TownQuests questsAfterHunterRawLootOutstanding() {
        TownQuests quests = questsWithDayOneBatches();
        completeBasics(quests);

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();
        quests.addTutorialBatches(view);

        ResourceLocation hunterJob = JobID.toRL(new JobID("hunter", "sword"));
        quests.questBatches.markRecipeAsComplete(null, hunterJob);
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
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                        && q.getWantedId().equals(JobID.toRL(TownQuests.ONBOARDING_COOK_JOB))
        ));
        assertFalse(hasCookFollowUpQuest(all), "phase 2.5 batch must not carry the cook follow-up quests");
        // The cook needs kitchen_small, so the batch carries that room quest.
        ResourceLocation kitchenSmall = new ResourceLocation("questown", "kitchen_small");
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.ROOM
                        && q.getWantedId().equals(kitchenSmall)
        ), "phase 2.5 batch must carry the kitchen_small room quest");
        ResourceLocation storeRoomMedium = new ResourceLocation("questown", "store_room_medium");
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.ROOM
                        && storeRoomMedium.equals(q.getWantedId())
                        && q.fromRecipeID().map(new ResourceLocation("questown", "store_room")::equals).orElse(false)
        ), "phase 2.5 batch must carry the Small Storeroom -> Storeroom upgrade quest");
        assertTrue(view.getTriggersFired().contains(TutorialTrigger.Triggers.FirstRoomUpgrade),
                "the upgrades lesson opens with the batch that carries the first upgrade");
    }

    @Test
    void cookFollowUp_notPostedBeforeTheCookIsUnlocked() {
        TownQuests quests = questsAtPhase2_5();
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(2).build();
        quests.addTutorialBatches(view);

        quests.postCookFollowUpIfDue(view);

        assertFalse(hasCookFollowUpQuest(quests.questBatches.getAll()));

        quests.postCookFollowUpIfDue(view);

        assertFalse(hasCookFollowUpQuest(quests.questBatches.getAll()));
    }

    @Test
    void cookFollowUp_postedOnceTheCookIsUnlocked() {
        TownQuests quests = questsAtPhase2_5();

        quests.postCookFollowUpIfDue(view);

        List<MCQuest> all = quests.questBatches.getAll();
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.SKILL_UNLOCK
                        && "cook".equals(MCQuest.skillTreeRoot(q.getWantedId()))
        ), "follow-up must carry the cook skill-unlock quest");
        // The follow-up brings villager 3, so the bedroom becomes a big bedroom.
        assertTrue(all.stream().anyMatch(q ->
                q.getType() == Quest.QuestType.ROOM
                        && bigBedroom.equals(q.getWantedId())
                        && q.fromRecipeID().map(bedroom::equals).orElse(false)
        ), "the follow-up (brings villager 3) must carry the Bedroom -> Big Bedroom upgrade");
    }

    @Test
    void cookFollowUp_postedOnlyOnce() {
        TownQuests quests = questsAtPhase2_5();
        TestTutorialTownView view = TestTutorialTownView.builder().villagerCount(2)
                .villagerRoots("hunter", "cook").build();
        quests.addTutorialBatches(view);
        quests.questBatches.markRecipeAsComplete(null, JobID.toRL(TownQuests.ONBOARDING_COOK_JOB));

        quests.postCookFollowUpIfDue(view);
        quests.postCookFollowUpIfDue(view);

        long skillQuests = quests.questBatches.getAll().stream()
                .filter(q -> q.getType() == Quest.QuestType.SKILL_UNLOCK)
                .count();
        assertEquals(1, skillQuests);
    }

    private static boolean hasCookFollowUpQuest(List<MCQuest> all) {
        return all.stream().anyMatch(q ->
                (q.getType() == Quest.QuestType.LOOT
                        && q.getWantedId().equals(TagsInit.Items.VILLAGER_COOKED_FOOD.location()))
                        || q.getType() == Quest.QuestType.SKILL_UNLOCK
                        || new ResourceLocation("questown", "big_bedroom").equals(q.getWantedId())
        );
    }

    @Test
    void whenRawFoodLootNotComplete_phase2_5Skipped() {
        TownQuests quests = questsAfterHunterRawLootOutstanding();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        // Phase 2.5 waits for the raw food.
        assertEquals(TownQuests.Tutorial.SKIPPED, result);
        boolean cookAdded = quests.questBatches.getAll().stream().anyMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                        && q.getWantedId().equals(JobID.toRL(TownQuests.ONBOARDING_COOK_JOB))
        );
        assertFalse(cookAdded, "cook job change should not be added before raw food is produced");
    }

    @Test
    void whenHunterDoneAndTwoVillagers_jobChangeAndFoodAdded() {
        TownQuests quests = questsAtPhase2_5();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.APPLIED, result);
        assertTrue(quests.questBatches.getAll().stream().anyMatch(q ->
                q.getType() == Quest.QuestType.JOB_CHANGE
                        && q.getWantedId().equals(JobID.toRL(TownQuests.ONBOARDING_COOK_JOB))
        ));
    }

    @Test
    void whenHunterDoneButOneVillager_noAdvance() {
        TownQuests quests = questsAtPhase2_5();

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(1)
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
        quests.addTutorialBatches(view);

        quests.questBatches.markRecipeAsComplete(null, JobID.toRL(TownQuests.ONBOARDING_COOK_JOB));
        quests.questBatches.markRecipeAsComplete(null, TagsInit.Items.VILLAGER_COOKED_FOOD.location());

        // Two job changes done: the miner lesson posts.
        quests.addTutorialBatches(view);
        completeMinerBeat(quests);

        return quests;
    }

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

        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);
        assertEquals(TownQuests.Tutorial.APPLIED, result);
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

        // One job change only: the miner lesson doesn't post.
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

    private TownQuests questsAfterSupplyChain() {
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
    void whenSupplyChainDone_tutorialDone_secondJobTypeTriggerFired() {
        TownQuests quests = questsAfterSupplyChain();
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(3)
                .build();

        TownQuests.Tutorial result = quests.addTutorialBatches(view);

        assertEquals(TownQuests.Tutorial.DONE, result, "nothing after the supply chain (phase 7 cut)");
        assertTrue(view.getTriggersFired().contains(TutorialTrigger.Triggers.SecondJobType));
        ResourceLocation storeRoomMedium = new ResourceLocation("questown", "store_room_medium");
        assertEquals(1, quests.questBatches.getAll().stream().filter(q ->
                storeRoomMedium.equals(q.getWantedId()) && q.getType() == Quest.QuestType.ROOM
        ).count(), "exactly one Small Storeroom -> Storeroom upgrade quest");
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
                TutorialTownView.PHASE_5
        );

        TownQuests quests = questsAfterHunterGathererDone();
        TestTutorialTownView view = TestTutorialTownView.builder()
                .villagerCount(2)
                .build();
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

    @Test
    void campfireBatch_isNotProceduralBatch() {
        // Regression: placing a campfire (the very first step) was instantly
        // broadcasting "your village is thriving" because campfire batch
        // completion was counted as a procedural batch milestone.
        MCQuestBatch campfireBatch = new MCQuestBatch(null, null, new NoOpReward());
        campfireBatch.addNewQuest(null, SpecialQuests.CAMPFIRE);
        assertFalse(TownQuests.isProceduralBatch(campfireBatch));
    }

    private static final ResourceLocation STORE_ROOM_MEDIUM = new ResourceLocation("questown", "store_room_medium");
    private static final ResourceLocation CRAFTING_ROOM = new ResourceLocation("questown", "crafting_room");

    /** Completes onboarding quests whose recipe a room contains, as {@code roomRecipeCreated} does. */
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

        applySupersetCompletion(quests,
                SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM,
                new ResourceLocation("questown", "store_room_big"),
                new ResourceLocation("questown", "store_room_huge"));

        assertTrue(isComplete(quests, SpecialQuests.STORE_ROOM_SMALL));
    }

    @Test
    void superset_randomSmallStoreroomQuest_notCompletedByHugeStoreroom() {
        MCQuest randomSmallQuest = MCQuest.standalone(null, null, SpecialQuests.STORE_ROOM_SMALL);
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

        applySupersetCompletion(quests,
                SpecialQuests.STORE_ROOM_SMALL, STORE_ROOM_MEDIUM,
                new ResourceLocation("questown", "store_room_big"),
                new ResourceLocation("questown", "store_room_huge"));

        assertFalse(isComplete(quests, CRAFTING_ROOM));
    }

}
