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

    private static MCQuestBatch onboardingJobChangeBatch(
            JobID wantedJob,
            VillagerUUID owner
    ) {
        UUID batchUUID = UUID.randomUUID();
        MCQuest q = MCQuest.jobChange(batchUUID, owner, wantedJob);
        q.setOnboarding(true);
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

    private static TownQuests withBatches(MCQuestBatch... batches) {
        TownQuests quests = new TownQuests();
        for (MCQuestBatch batch : batches) {
            quests.questBatches.add(batch);
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
        }
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

    // The onboarding job-change guarantee (ruling D): an open onboarding JOB_CHANGE quest's
    // root fills the BOP-spend's guarantee slot, the way a blessed BOP's NBT does.

    @Test
    void onboarding_openQuest_isTheGuaranteeForAnyTownie() {
        TownQuests quests = withBatches(onboardingJobChangeBatch(new JobID("cook", "cook"), null));
        VillagerUUID villager = VillagerUUID.random();

        assertEquals(new JobID("cook", "cook"), quests.onboardingJobChangeGuarantee(villager)
                                                       .orElseThrow(),
                "an unowned open onboarding quest guarantees its root for any townie");
    }

    @Test
    void onboarding_ownedQuest_guaranteesOnlyForTheOwner() {
        VillagerUUID owner = VillagerUUID.random();
        VillagerUUID other = VillagerUUID.random();
        TownQuests quests = withBatches(onboardingJobChangeBatch(new JobID("cook", "cook"), owner));

        assertEquals(new JobID("cook", "cook"), quests.onboardingJobChangeGuarantee(owner)
                                                       .orElseThrow());
        assertTrue(quests.onboardingJobChangeGuarantee(other).isEmpty(),
                "a quest owned by another townie is not this townie's promise");
    }

    @Test
    void onboarding_completedQuest_noLongerGuarantees() {
        VillagerUUID villager = VillagerUUID.random();
        MCQuestBatch batch = onboardingJobChangeBatch(new JobID("hunter", "hunter"), null);
        TownQuests quests = withBatches(batch);

        batch.markRecipeAsComplete(null, batch.getAll().get(0).getWantedId());

        assertTrue(quests.onboardingJobChangeGuarantee(villager).isEmpty(),
                "once the onboarding job change is done, spends fall back to blessing/pre-roll");
    }

    @Test
    void onboarding_nonOnboardingQuest_doesNotGuarantee() {
        VillagerUUID villager = VillagerUUID.random();
        // A plain (non-onboarding) JOB_CHANGE quest - the post-tutorial world: the BOP's own
        // blessing and the pre-roll decide, not an open quest.
        TownQuests quests = withBatches(jobChangeBatch(new JobID("cook", "cook"), null));

        assertTrue(quests.onboardingJobChangeGuarantee(villager).isEmpty());
    }

    @Test
    void onboarding_oldestOpenQuestWins() {
        VillagerUUID villager = VillagerUUID.random();
        TownQuests quests = withBatches(
                onboardingJobChangeBatch(new JobID("hunter", "hunter"), null),
                onboardingJobChangeBatch(new JobID("cook", "cook"), null)
        );

        assertEquals(new JobID("hunter", "hunter"), quests.onboardingJobChangeGuarantee(villager)
                                                           .orElseThrow(),
                "posted order: the hunter batch precedes the cook phase, so hunter is promised first");
    }

    @Test
    void onboarding_guaranteedRoot_completesTheQuestAtSpend() {
        VillagerUUID villager = VillagerUUID.random();
        TownQuests quests = withBatches(onboardingJobChangeBatch(new JobID("cook", "cook"), null));

        // The spend's decided root comes from the guarantee; the same moment completes the quest.
        boolean completed = quests.markJobChangeCompleteAtSpend(
                villager, quests.onboardingJobChangeGuarantee(villager).orElseThrow());

        assertTrue(completed, "the onboarding spend resolves the promised job, not a pre-roll");
        assertEquals(villager, quests.questBatches.getAllBatches().get(0).getOwner());
    }
}
