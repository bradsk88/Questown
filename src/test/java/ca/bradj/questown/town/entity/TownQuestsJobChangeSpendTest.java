package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.QuestBatch;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The JOB_CHANGE quest completes at the BOP-spend moment (E1: reward-on-the-spend-moment),
 * not when the root change applies in the morning. Only the quest whose wanted root matches
 * the decided pending root (blessed guarantee or pre-roll) completes, and never one already
 * owned by a different townie.
 */
class TownQuestsJobChangeSpendTest {

    private static MCQuestBatch jobChangeBatch(
            JobID wantedJob,
            VillagerUUID owner
    ) {
        UUID batchUUID = UUID.randomUUID();
        MCQuest q = MCQuest.jobChange(batchUUID, owner, wantedJob);
        // Inputs drives the batch through its normal construction path (quest + reward).
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, owner);
        inputs.addNewQuest(q);
        return inputs.withRewardUponCompletion(new NoOpReward());
    }

    private static TownQuests withBatch(MCQuestBatch batch) {
        TownQuests quests = new TownQuests();
        quests.questBatches.add(batch);
        // QuestBatches.add re-points the batch's completion listener to the town chain
        // (TownQuests.questCompleted needs the live flag). Re-point it back to a no-op so the
        // pure completion decision is testable headless; the flag wiring is exercised by the
        // autotest suite instead.
        batch.addChangeListener(new QuestBatch.ChangeListener<MCQuest>() {
            @Override
            public void questCompleted(MCQuest quest) {
            }

            @Override
            public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
            }

            @Override
            public void questLost(MCQuest quest) {
            }
        });
        return quests;
    }

    @Test
    void spend_completesUnownedQuestForTheMatchingRoot() {
        TownQuests quests = withBatch(jobChangeBatch(new JobID("hunter", "hunter"), null));
        VillagerUUID villager = VillagerUUID.random();

        boolean completed = quests.markJobChangeCompleteAtSpend(villager, new JobID("hunter", "hunter"));

        assertTrue(completed, "the matching open quest completes at spend time");
        MCQuestBatch batch = quests.questBatches.getAllBatches().get(0);
        assertTrue(batch.getAll().get(0).isComplete());
        assertEquals(villager, batch.getOwner(), "the spending townie owns the quest");
    }

    @Test
    void spend_doesNotCompleteQuestOwnedByAnotherTownie() {
        VillagerUUID other = VillagerUUID.random();
        TownQuests quests = withBatch(jobChangeBatch(new JobID("hunter", "hunter"), other));
        VillagerUUID villager = VillagerUUID.random();

        boolean completed =
                quests.markJobChangeCompleteAtSpend(villager, new JobID("hunter", "hunter"));

        assertFalse(completed,
                "one BOP is one townie's change: a quest owned by another townie does not complete");
        assertFalse(quests.questBatches.getAllBatches().get(0).getAll().get(0).isComplete());
    }

    @Test
    void spend_doesNotCompleteQuestForADifferentRoot() {
        TownQuests quests = withBatch(jobChangeBatch(new JobID("cook", "cook"), null));
        VillagerUUID villager = VillagerUUID.random();

        boolean completed = quests.markJobChangeCompleteAtSpend(villager, new JobID("hunter", "hunter"));

        assertFalse(completed, "a pre-roll that did not land on the wanted root completes nothing");
    }

    @Test
    void spend_rootMatch_completesQuestWantedByASubJob() {
        // The wanted quest names a specific sub-job (cook/mutton); the pending root is the
        // family root (cook). processJobChanges' sameRoot fallback is mirrored here.
        TownQuests quests = withBatch(jobChangeBatch(new JobID("cook", "mutton"), null));
        VillagerUUID villager = VillagerUUID.random();

        boolean completed = quests.markJobChangeCompleteAtSpend(villager, new JobID("cook", "cook"));

        assertTrue(completed, "a family-root spend completes a sub-job-wanted quest");
    }

    @Test
    void spend_completedQuest_isNotCompletedAgain() {
        TownQuests quests = withBatch(jobChangeBatch(new JobID("hunter", "hunter"), null));
        VillagerUUID villager = VillagerUUID.random();

        quests.markJobChangeCompleteAtSpend(villager, new JobID("hunter", "hunter"));
        boolean second = quests.markJobChangeCompleteAtSpend(villager, new JobID("hunter", "hunter"));

        assertFalse(second, "an already-complete quest does not complete twice");
    }
}
