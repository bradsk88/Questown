package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-17 playtest (05:38:35): "The 'change profession' tab should have a red dot when
 * a job change quest is active and the villager is a gatherer."
 * {@link TownQuests#wantsJobChangeFrom(JobID)} is the server-side rule behind that dot.
 */
class TownQuestsJobChangeAttentionTest {

    private static final JobID GATHERER = new JobID("gatherer", "gather");
    private static final JobID HUNTER = new JobID("hunter", "sword");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void openJobChangeQuestAndGatherer_wantsAttention() {
        assertTrue(townWith(MCQuest.jobChange(UUID.randomUUID(), null, HUNTER)).wantsJobChangeFrom(GATHERER));
    }

    @Test
    void noJobChangeQuest_noAttention() {
        assertFalse(new TownQuests().wantsJobChangeFrom(GATHERER));
    }

    @Test
    void completedJobChangeQuest_noAttention() {
        MCQuest done = MCQuest.jobChange(UUID.randomUUID(), null, HUNTER).completed(null);
        assertFalse(townWith(done).wantsJobChangeFrom(GATHERER));
    }

    @Test
    void villagerAlreadyHasAJob_noAttention() {
        assertFalse(townWith(MCQuest.jobChange(UUID.randomUUID(), null, HUNTER)).wantsJobChangeFrom(HUNTER));
    }

    private static TownQuests townWith(MCQuest q) {
        TownQuests quests = new TownQuests();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(UUID.randomUUID(), null);
        inputs.addNewQuest(q);
        quests.addBatch(inputs.withRewardUponCompletion(new NoOpReward()));
        return quests;
    }
}
