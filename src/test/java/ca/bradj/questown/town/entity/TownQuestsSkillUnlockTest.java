package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatch;
import ca.bradj.questown.town.quests.QuestBatches;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cook batch's SKILL_UNLOCK quest: "unlock any skill on the cook's skill
 * tree", beside the cooked-food quest it helps fill. It completes when the player spends a Block of
 * Progress on a skill-tree node of that root ({@code UnlockJobMessage}), which teaches skill
 * unlocking.
 */
class TownQuestsSkillUnlockTest {

    private static final String COOK_TREE = "cook";

    private static final QuestBatch.ChangeListener<MCQuest> NO_OP_LISTENER = new QuestBatch.ChangeListener<>() {
        @Override
        public void questCompleted(MCQuest quest) {
        }

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        }

        @Override
        public void questLost(MCQuest quest) {
        }
    };

    private static TownQuests townWithCookSkillQuest() {
        TownQuests quests = new TownQuests();
        // The real listener (TownQuests) broadcasts through the town, which a unit test lacks.
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(MCQuest.skillUnlock(batchUUID, null, COOK_TREE));
        quests.questBatches.add(inputs.withRewardUponCompletion(new NoOpReward()));
        return quests;
    }

    private static MCQuest skillQuest(TownQuests quests) {
        return quests.questBatches.getAll().stream()
                                  .filter(q -> q.getType() == Quest.QuestType.SKILL_UNLOCK)
                                  .findFirst()
                                  .orElseThrow();
    }

    @Test
    void unlockingACookSkill_completesTheQuest() {
        TownQuests quests = townWithCookSkillQuest();

        quests.markSkillUnlocked(new JobID("cook", "fish"));

        assertTrue(skillQuest(quests).isComplete());
    }

    @Test
    void unlockingAnotherRootsSkill_leavesTheQuestOpen() {
        TownQuests quests = townWithCookSkillQuest();

        quests.markSkillUnlocked(new JobID("miner", "half_day"));

        assertFalse(skillQuest(quests).isComplete());
    }

    @Test
    void skillUnlockQuestKeepsItsTypeThroughInitialize() {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(MCQuest.skillUnlock(batchUUID, null, COOK_TREE));
        MCQuestBatch batch = inputs.withRewardUponCompletion(new NoOpReward());

        TownQuests town = new TownQuests();
        town.questBatches.initialize(new QuestBatches.VillagerProvider<>() {
            @Override
            public UUID getRandomVillager() {
                return null;
            }

            @Override
            public boolean isVillagerMissing(UUID uuid) {
                return true;
            }
        }, ImmutableList.of(batch));

        assertEquals(COOK_TREE, MCQuest.skillTreeRoot(skillQuest(town).getWantedId()));
    }

    /**
     * Batch completion matches by wanted id alone. The first build used the cook JOB_CHANGE quest's
     * id (cook/extract), so in the cook batch completing the job change could close the skill quest.
     */
    @Test
    void completingTheCookJobChange_leavesTheSkillQuestOpen() {
        TownQuests quests = new TownQuests();
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(MCQuest.skillUnlock(batchUUID, null, COOK_TREE));
        inputs.addNewQuest(MCQuest.jobChange(batchUUID, null, TownQuests.ONBOARDING_COOK_JOB));
        quests.questBatches.add(inputs.withRewardUponCompletion(new NoOpReward()));

        quests.questBatches.markRecipeAsComplete(null, JobID.toRL(TownQuests.ONBOARDING_COOK_JOB));

        assertFalse(skillQuest(quests).isComplete());
    }

    /** A curated path's skill quest names one skill: only that node completes it. */
    @Test
    void namedSkillQuest_completesOnlyForItsSkill() {
        TownQuests quests = new TownQuests();
        quests.questBatches.addChangeListener(NO_OP_LISTENER);
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        JobID shovel = new JobID("crafter", "wooden_shovel");
        JobID sword = new JobID("crafter", "wooden_sword");
        inputs.addNewQuest(MCQuest.namedSkillUnlock(batchUUID, null, shovel));
        inputs.addNewQuest(MCQuest.namedSkillUnlock(batchUUID, null, sword));
        quests.questBatches.add(inputs.withRewardUponCompletion(new NoOpReward()));

        quests.markSkillUnlocked(shovel);

        Map<JobID, Boolean> done = new HashMap<>();
        quests.questBatches.getAll().forEach(q ->
                done.put(MCQuest.namedSkill(q.getWantedId()).orElseThrow(), q.isComplete()));
        assertEquals(Map.of(shovel, true, sword, false), done);
    }

    @Test
    void namedSkillQuest_namesItsTreeAndSkill() {
        JobID sword = new JobID("crafter", "wooden_sword");
        MCQuest q = MCQuest.namedSkillUnlock(UUID.randomUUID(), null, sword);

        assertEquals("crafter", MCQuest.skillTreeRoot(q.getWantedId()));
        assertEquals(Optional.of(sword), MCQuest.namedSkill(q.getWantedId()));
        assertEquals(Optional.empty(),
                MCQuest.namedSkill(MCQuest.skillUnlock(UUID.randomUUID(), null, COOK_TREE).getWantedId()));
    }

    @Test
    void skillTreeRoot_readsTheFirstBuildsJobIdToo() {
        assertEquals("cook", MCQuest.skillTreeRoot(JobID.toRL(new JobID("cook", "extract"))));
    }
}
