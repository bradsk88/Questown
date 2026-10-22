package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.Reward;
import ca.bradj.questown.town.rewards.RewardType;
import ca.bradj.questown.town.quests.QuestBatch;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownQuestsJobUnlockTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    // --- chooseProducerRoot (pure) ---

    @Test
    void chooseProducerRootPicksFirstSortedCandidate() {
        Set<String> producers = Set.of("farmer", "baker");
        assertEquals(
                "baker",
                TownQuests.chooseProducerRoot(producers, "hunter", Set.of())
        );
    }

    @Test
    void chooseProducerRootExcludesStuckRootItself() {
        // The stuck root already holds the capacity; re-suggesting it would not help.
        assertNull(
                TownQuests.chooseProducerRoot(Set.of("farmer"), "farmer", Set.of())
        );
    }

    @Test
    void chooseProducerRootExcludesUnlockedRoots() {
        // A root some townie has unlocked is not something to ask for.
        assertNull(
                TownQuests.chooseProducerRoot(Set.of("farmer", "baker"), "hunter", Set.of("farmer", "baker"))
        );
        assertEquals(
                "farmer",
                TownQuests.chooseProducerRoot(Set.of("farmer", "baker"), "hunter", Set.of("baker"))
        );
    }

    @Test
    void chooseProducerRootEmptyWhenNoProducers() {
        assertNull(TownQuests.chooseProducerRoot(Set.of(), "farmer", Set.of()));
    }

    // --- quest plumbing: JOB_UNLOCK through the real batch/quest classes ---

    private static MCQuestBatch jobUnlockBatch(JobID rootJob) {
        UUID batchUUID = UUID.randomUUID();
        MCQuest q = MCQuest.jobUnlock(batchUUID, null, JobID.toRL(rootJob));
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(q);
        return inputs.withRewardUponCompletion(new NoOpReward());
    }

    @Test
    void jobUnlockQuestCarriesTypeAndWantedRoot() {
        MCQuest q = jobUnlockBatch(new JobID("farmer", "farmer")).getAll().get(0);
        assertEquals(Quest.QuestType.JOB_UNLOCK, q.getType());
        assertEquals(JobID.toRL(new JobID("farmer", "farmer")), q.getWantedId());
        assertFalse(q.isComplete());
    }

    @Test
    void jobUnlockQuestCompletesWhenRootUnlocked() {
        // Drives the real QuestBatch completion path (factory.completed, reward claim);
        // the TownQuests wiring around it is exercised in a held run.
        MCQuestBatch batch = jobUnlockBatch(new JobID("farmer", "farmer"));
        ResourceLocation wanted = JobID.toRL(new JobID("farmer", "farmer"));
        assertTrue(batch.markRecipeAsComplete(null, wanted));

        MCQuest after = batch.getAll().get(0);
        assertEquals(Quest.QuestStatus.COMPLETED, after.getStatus());
        assertEquals(Quest.QuestType.JOB_UNLOCK, after.getType());
    }

    @Test
    void withdrawQuestRemovesOutrightWithNoPlaceholder() {
        // The markRecipeAsLostByKey path replaced the quest with a lost() placeholder
        // re-typed as an ACTIVE room quest that no completion path ever cleared (the
        // handle-accumulation half of the JOB_UNLOCK ping-pong). Withdraw removes it.
        MCQuestBatch batch = jobUnlockBatch(new JobID("farmer", "farmer"));
        MCQuest q = batch.getAll().get(0);
        assertTrue(batch.withdrawQuest(q));
        assertTrue(batch.getAll().isEmpty());
        assertFalse(batch.withdrawQuest(q));
    }

    @Test
    void withdrawQuestNotifiesListener() {
        MCQuestBatch batch = jobUnlockBatch(new JobID("farmer", "farmer"));
        MCQuest q = batch.getAll().get(0);
        LostRecorder recorder = new LostRecorder();
        batch.addChangeListener(recorder);
        batch.withdrawQuest(q);
        assertEquals(q, recorder.lost);
    }

    static class LostRecorder implements QuestBatch.ChangeListener<MCQuest> {
        MCQuest lost;

        @Override
        public void questCompleted(MCQuest quest) {
        }

        @Override
        public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {
        }

        @Override
        public void questLost(MCQuest quest) {
            this.lost = quest;
        }
    }

    // --- stuck-root encoding + in-place retarget ---

    @Test
    void jobUnlockStuckRootEncodingRoundTrips() {
        MCQuest q = MCQuest.jobUnlock(UUID.randomUUID(), null, JobID.toRL(new JobID("crafter", "stick")));
        assertNull(q.unlockStuckRoot().orElse(null));
        q.setUnlockStuckRoot("gatherer");
        assertEquals("gatherer", q.unlockStuckRoot().orElseThrow());
        assertEquals(JobID.toRL(new JobID("gatherer", "gatherer")), q.fromRecipeID().orElseThrow());
    }

    @Test
    void jobUnlockStuckRootSurvivesNbtRoundTrip() {
        MCQuest q = MCQuest.jobUnlock(UUID.randomUUID(), null, JobID.toRL(new JobID("crafter", "stick")));
        q.setUnlockStuckRoot("gatherer");
        CompoundTag tag = MCQuest.SERIALIZER.serializeNBT(q);
        MCQuest back = MCQuest.SERIALIZER.deserializeNBT(tag);
        assertEquals("gatherer", back.unlockStuckRoot().orElseThrow());
    }

    @Test
    void retargetUnlockSwapsProducerInPlace() {
        MCQuest q = MCQuest.jobUnlock(UUID.randomUUID(), null, JobID.toRL(new JobID("crafter", "stick")));
        q.retargetUnlock(JobID.toRL(new JobID("baker", "bread")), "new flavor");
        assertEquals(JobID.toRL(new JobID("baker", "bread")), q.getWantedId());
        assertEquals("new flavor", q.getFlavorText());
        assertEquals(Quest.QuestType.JOB_UNLOCK, q.getType());
    }

    @Test
    void retargetUnlockRejectsOtherTypes() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, new ResourceLocation("questown", "store_room"));
        assertThrows(
                IllegalStateException.class,
                () -> q.retargetUnlock(JobID.toRL(new JobID("baker", "bread")), "x")
        );
    }

    // --- retireJobUnlockQuests (dev 2026-10-22: post-onboarding quests are room quests only) ---

    private static final ResourceLocation MINER_ROOM = new ResourceLocation("questown", "mine");

    private static MCQuestBatch topNeedLegacyBatch() {
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch.Inputs inputs = new MCQuestBatch.Inputs(batchUUID, null);
        inputs.addNewQuest(MCQuest.jobUnlock(batchUUID, null, JobID.toRL(new JobID("miner", "miner"))));
        inputs.addNewQuest(MCQuest.standalone(batchUUID, null, MINER_ROOM));
        return inputs.withRewardUponCompletion(new NoOpReward());
    }

    @Test
    void retireWithdrawsAStuckRootUnlockQuestAndDeclinesItsEmptyBatch() {
        MCQuestBatch b = jobUnlockBatch(new JobID("baker", "baker"));
        b.getAll().get(0).setUnlockStuckRoot("gatherer");
        List<MCQuestBatch> declined = new ArrayList<>();

        TownQuests.retireJobUnlockQuests(List.of(b), declined::add);

        assertTrue(b.getAll().isEmpty());
        assertEquals(List.of(b), declined);
    }

    @Test
    void retireKeepsALegacyTopNeedRoomQuestTaggedWithItsProducer() {
        MCQuestBatch b = topNeedLegacyBatch();
        List<MCQuestBatch> declined = new ArrayList<>();

        TownQuests.retireJobUnlockQuests(List.of(b), declined::add);

        assertEquals(1, b.getAll().size());
        MCQuest room = b.getAll().get(0);
        assertEquals(MINER_ROOM, room.getWantedId());
        assertEquals("miner", room.topNeedProducer().orElseThrow());
        assertTrue(TownQuests.topNeedBeatOpen(b.getAll()));
        assertTrue(declined.isEmpty());
    }

    @Test
    void retireLeavesCompletedUnlockQuestsAlone() {
        MCQuestBatch b = jobUnlockBatch(new JobID("baker", "baker"));
        b.markRecipeAsComplete(null, JobID.toRL(new JobID("baker", "baker")));

        TownQuests.retireJobUnlockQuests(List.of(b), x -> {});

        assertEquals(1, b.getAll().size());
    }

    // --- the top-need lock and the built-producer exclusion ---

    @Test
    void topNeedBeatOpenOnlyWhileItsRoomQuestIsOpen() {
        MCQuest open = MCQuest.standalone(UUID.randomUUID(), null, MINER_ROOM);
        open.setTopNeedProducer("miner");
        MCQuest untagged = MCQuest.standalone(UUID.randomUUID(), null, MINER_ROOM);

        assertTrue(TownQuests.topNeedBeatOpen(List.of(open)));
        assertFalse(TownQuests.topNeedBeatOpen(List.of(untagged)));
        assertFalse(TownQuests.topNeedBeatOpen(List.of(open.completed(null))));
    }

    @Test
    void aBuiltTopNeedRoomRetiresItsProducer() {
        MCQuest open = MCQuest.standalone(UUID.randomUUID(), null, MINER_ROOM);
        open.setTopNeedProducer("miner");

        assertEquals(Set.of(), TownQuests.builtTopNeedProducers(List.of(open)));
        assertEquals(Set.of("miner"), TownQuests.builtTopNeedProducers(List.of(open.completed(null))));
    }

    @Test
    void theTopNeedTagSurvivesNbtAndTheLostPlaceholder() {
        MCQuest q = MCQuest.standalone(UUID.randomUUID(), null, MINER_ROOM);
        q.setTopNeedProducer("miner");

        assertEquals("miner", MCQuest.SERIALIZER.deserializeNBT(MCQuest.SERIALIZER.serializeNBT(q)).topNeedProducer().orElseThrow());
        assertEquals("miner", q.lost().topNeedProducer().orElseThrow());
    }

    /** A claim no-op so the completion path can run in a unit test without a town. */
    static class NoOpReward extends MCReward {
        NoOpReward() {
            super(new RewardType<>(
                    (rType, town) -> new NoOpReward(),
                    new ResourceLocation("questown", "no_op")
            ));
        }

        @Override
        protected Reward.RewardApplier getApplier() {
            return () -> {
            };
        }

        @Override
        public boolean addsQuestsWhenApplied() {
            return false;
        }

        @Override
        protected CompoundTag serializeNbt() {
            return new CompoundTag();
        }

        @Override
        protected void deserializeNbt(TownInterface entity, CompoundTag tag) {
        }

        @Override
        public boolean contains(RewardType<?> reward) {
            return false;
        }

        @Override
        public String toNiceString() {
            return "NoOpReward";
        }
    }
}
