package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.QuestBatch;
import ca.bradj.questown.town.quests.QuestBatches;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The miner batch's SKILL_UNLOCK quest (dev, 2026-10-25): "unlock any skill on the cook's skill
 * tree". It completes when the player spends a Block of Progress on a skill-tree node of that root
 * ({@code UnlockJobMessage}), which teaches skill unlocking.
 */
class TownQuestsSkillUnlockTest {

    private static final JobID COOK_TREE = new JobID("cook", "extract");

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

        assertEquals(JobID.toRL(COOK_TREE), skillQuest(town).getWantedId());
    }
}
