package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.rewards.RewardType;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * Load-time replay of a completed batch must re-mark the quests complete WITHOUT re-claiming
 * the already-applied batch reward. The pre-fix replay went through
 * {@link QuestBatch#markRecipeAsComplete(Object, Object)}, which claims the reward when the
 * batch closes - on every load of a post-onboarding world that tripped the once-guard
 * ("Refusing to apply reward more than once" x3) plus a stale "instant-type reward loaded
 * from disk" WARN. Ticket:
 * .scratch/day1-playtest-2026-10-11/load-time-reward-reclaim-error-noise.md (option B).
 * <p>
 * The restored-batch construction mirrors the production deserializer exactly
 * ({@code new MCQuestBatch()} + {@code initialize(batchUUID, allQuests, reward)},
 * {@code MCQuestBatch.SERIALIZER.deserializeNBT}); the NBT byte layer is irrelevant to the
 * claim path, so the test builds the restored objects directly.
 */
class QuestBatchesLoadReplayTest {

    @Test
    void loadReplay_completedBatch_remarksQuest_withoutReclaimingReward() {
        RecordingReward reward = new RecordingReward();

        // On-disk state: one CAMPFIRE quest, complete; its batch reward claimed exactly once.
        UUID batchUUID = UUID.randomUUID();
        MCQuestBatch onDisk = new MCQuestBatch();
        onDisk.initialize(batchUUID, List.of(), reward);
        onDisk.addNewQuest(null, SpecialQuests.CAMPFIRE);
        MCQuestBatches source = new MCQuestBatches(MCQuestBatch::new);
        source.add(onDisk);
        source.markRecipeAsComplete(null, SpecialQuests.CAMPFIRE);
        Assertions.assertTrue(onDisk.getAll().get(0).isComplete());
        Assertions.assertTrue(reward.isApplied());
        Assertions.assertEquals(1, reward.claimAttempts);

        // The deserializer's view of the same save: a fresh batch holding the completed quest
        // and the same (already-applied) reward, then the rebuild through initialize().
        MCQuestBatch restored = new MCQuestBatch();
        restored.initialize(batchUUID, onDisk.getAll(), reward);
        MCQuestBatches reloaded = new MCQuestBatches(MCQuestBatch::new);
        reloaded.initialize(null, ImmutableList.of(restored));

        Assertions.assertEquals(
                1,
                reward.claimAttempts,
                "load replay must not re-claim the already-applied batch reward");
        Assertions.assertEquals(1, reloaded.getAll().size());
        Assertions.assertTrue(reloaded.getAll().get(0).isComplete());
    }

    /** Load the batch the way the flag does: deserialize, then the replay in initialize(). */
    private static MCQuest reloadOne(MCQuest saved) {
        RecordingReward reward = new RecordingReward();
        MCQuestBatch restored = new MCQuestBatch();
        restored.initialize(UUID.randomUUID(), List.of(saved), reward);
        MCQuestBatches reloaded = new MCQuestBatches(MCQuestBatch::new);
        reloaded.initialize(null, ImmutableList.of(restored));
        Assertions.assertEquals(1, reloaded.getAll().size());
        return reloaded.getAll().get(0);
    }

    // village/day1 2026-10-17: after a real flag reload every quest read onboarding=false, the
    // arrival gate (TownQuests.isArrivalGateOpen) opened, and villager 2 arrived before Raw Food.
    @Test
    void loadReplay_keepsTheOnboardingMarker() {
        MCQuest saved = MCQuest.standalone(UUID.randomUUID(), null, SpecialQuests.TOWN_GATE);
        saved.setOnboarding(true);
        Assertions.assertTrue(reloadOne(saved).isOnboarding());
    }

    @Test
    void loadReplay_keepsTheFlavorText() {
        MCQuest saved = MCQuest.standalone(UUID.randomUUID(), null, SpecialQuests.TOWN_GATE);
        saved.setFlavorText("quests.flavor.test");
        Assertions.assertEquals("quests.flavor.test", reloadOne(saved).getFlavorText());
    }

    @Test
    void loadReplay_keepsLootProgress() {
        MCQuest saved = MCQuest.loot(UUID.randomUUID(), null, new ResourceLocation("questown", "villager_food"), 8);
        saved.addExtracted(5);
        Assertions.assertEquals(5, reloadOne(saved).getExtracted());
    }

    static class RecordingReward extends MCReward {

        static final RewardType<RecordingReward> TYPE = new RewardType<>(
                (rType, entity) -> new RecordingReward(),
                new ResourceLocation("questown", "test_recording")
        );

        int claimAttempts = 0;

        RecordingReward() {
            super(TYPE);
        }

        @Override
        void claim() {
            this.claimAttempts++;
            super.claim();
        }

        @Override
        protected @NotNull RewardApplier getApplier() {
            return () -> {};
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
        public String toNiceString() {
            return "Recording";
        }

        @Override
        public boolean contains(@NotNull RewardType<?> reward) {
            return false;
        }
    }
}
