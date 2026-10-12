package ca.bradj.questown.town.entity;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.quests.Reward;
import ca.bradj.questown.town.rewards.RewardType;
import ca.bradj.questown.town.quests.QuestBatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
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
    void rootNowUnlockedFindsWorkUnderRoot() {
        VillagerUUID uuid = VillagerUUID.from(UUID.randomUUID());
        assertTrue(TownQuests.rootNowUnlocked(
                "farmer",
                ImmutableMap.of(uuid, ImmutableSet.of(new JobID("farmer", "grain")))
        ));
        assertFalse(TownQuests.rootNowUnlocked(
                "farmer",
                ImmutableMap.of(uuid, ImmutableSet.of(new JobID("hunter", "sword")))
        ));
        assertFalse(TownQuests.rootNowUnlocked(
                "farmer",
                ImmutableMap.of()
        ));
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

    // --- reconcileUnlockQuests (pure) ---

    @Test
    void reconcileRetargetsInPlaceWhenProducerFlips() {
        // The gatherer's missing item oscillates rods (crafter) -> food (baker):
        // the open quest is retargeted, not lost-and-reposted.
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of("q1", "gatherer"),
                Set.of(),
                Map.of("q1", "crafter"),
                Map.of("gatherer", "baker"),
                Set.of("gatherer")
        );
        assertEquals(Map.of("q1", "baker"), r.retarget());
        assertTrue(r.withdraw().isEmpty());
        assertTrue(r.post().isEmpty());
    }

    @Test
    void reconcileKeepsQuestWhenRootStillMapsToItsProducer() {
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of("q1", "gatherer"),
                Set.of(),
                Map.of("q1", "crafter"),
                Map.of("gatherer", "crafter"),
                Set.of("gatherer")
        );
        assertTrue(r.withdraw().isEmpty());
        assertTrue(r.retarget().isEmpty());
        assertTrue(r.post().isEmpty());
    }

    @Test
    void reconcileWithdrawsQuestWhenRootResolves() {
        // No stuck root left (the player stocked the item): the quest is withdrawn.
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of("q1", "gatherer"),
                Set.of(),
                Map.of("q1", "crafter"),
                Map.of(),
                Set.of()
        );
        assertEquals(Set.of("q1"), r.withdraw());
        assertTrue(r.retarget().isEmpty());
        assertTrue(r.post().isEmpty());
    }

    @Test
    void reconcileWithdrawsRootedQuestWhenNoViableProducer() {
        // The root is still stuck, but its missing item has no unlockable producer:
        // an unlock quest cannot help, so the quest is withdrawn.
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of("q1", "gatherer"),
                Set.of(),
                Map.of("q1", "crafter"),
                Map.of(),
                Set.of("gatherer")
        );
        assertEquals(Set.of("q1"), r.withdraw());
        assertTrue(r.post().isEmpty());
    }

    @Test
    void reconcilePostsForNewRootPastGrace() {
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of("gatherer", "crafter"),
                Set.of("gatherer")
        );
        assertEquals(Map.of("gatherer", "crafter"), r.post());
        assertTrue(r.withdraw().isEmpty());
    }

    @Test
    void reconcileDoesNotPostForRootWithOpenQuest() {
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of("q1", "gatherer"),
                Set.of(),
                Map.of("q1", "crafter"),
                Map.of("gatherer", "crafter"),
                Set.of("gatherer")
        );
        assertTrue(r.post().isEmpty());
        assertTrue(r.withdraw().isEmpty());
    }

    @Test
    void reconcileDoesNotPostForRootInsideGrace() {
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of("gatherer", "crafter"),
                Set.of()
        );
        assertTrue(r.post().isEmpty());
    }

    @Test
    void reconcileTownWideQuestKeptWhileSomeRootMapsToProducer() {
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of(),
                Set.of("q2"),
                Map.of("q2", "baker"),
                Map.of("gatherer", "baker"),
                Set.of()
        );
        assertTrue(r.withdraw().isEmpty());
        assertTrue(r.post().isEmpty());
    }

    @Test
    void reconcileTownWideQuestPinnedEvenWhenNoRootMapsToProducer() {
        // Lock-until-complete (day-1 playtest): the town-wide top-need quest is pinned to
        // its producer until processJobUnlocks completes it - it is NOT withdrawn or
        // retargeted when the demand ranking flips. Here the only stuck root maps to
        // crafter (not the quest's producer, baker), and no rooted quest is past grace,
        // so nothing is posted; the town-wide quest (q2) must survive untouched.
        TownQuests.UnlockReconcile r = TownQuests.reconcileUnlockQuests(
                Map.of(),
                Set.of("q2"),
                Map.of("q2", "baker"),
                Map.of("gatherer", "crafter"),
                Set.of()
        );
        assertTrue(r.withdraw().isEmpty());
        assertTrue(r.retarget().isEmpty());
        assertTrue(r.post().isEmpty());
    }

    // --- openTownWideJobUnlockQuestExists (the top-need lock-until-complete guard) ---

    @Test
    void openTownWideJobUnlockQuestTrueWhenTownWideOpen() {
        TownQuests quests = new TownQuests();
        quests.questBatches.add(jobUnlockBatch(new JobID("baker", "baker")));
        assertTrue(quests.openTownWideJobUnlockQuestExists());
    }

    @Test
    void openTownWideJobUnlockQuestFalseWhenOnlyRootedOpen() {
        // A rooted unlock quest (carries a stuck root) does NOT count as the
        // town-wide top-need quest, so the guard stays false and a new top-need
        // quest may still be posted.
        TownQuests quests = new TownQuests();
        MCQuestBatch b = jobUnlockBatch(new JobID("baker", "baker"));
        b.getAll().get(0).setUnlockStuckRoot("gatherer");
        quests.questBatches.add(b);
        assertFalse(quests.openTownWideJobUnlockQuestExists());
    }

    @Test
    void openTownWideJobUnlockQuestFalseWhenComplete() {
        TownQuests quests = new TownQuests();
        MCQuestBatch b = jobUnlockBatch(new JobID("baker", "baker"));
        ResourceLocation wanted = JobID.toRL(new JobID("baker", "baker"));
        b.markRecipeAsComplete(null, wanted);
        quests.questBatches.add(b);
        assertFalse(quests.openTownWideJobUnlockQuestExists());
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
