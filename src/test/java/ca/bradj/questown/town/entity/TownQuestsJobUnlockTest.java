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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void jobUnlockQuestMarksLostByKey() {
        MCQuestBatch batch = jobUnlockBatch(new JobID("farmer", "farmer"));
        ResourceLocation wanted = JobID.toRL(new JobID("farmer", "farmer"));
        assertTrue(batch.markRecipeAsLostByKey(wanted));

        // Pre-existing lost() semantics (shared by all quest types, see the TODO on
        // MCQuest.lost): the journal keeps a placeholder, not a LOST-status quest.
        // What matters for this feature: no open JOB_UNLOCK remains, so a re-stuck
        // root can get a fresh quest.
        assertEquals(1, batch.getAll().size());
        assertFalse(batch.getAll().stream()
                .anyMatch(v -> v.getType() == Quest.QuestType.JOB_UNLOCK));
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
