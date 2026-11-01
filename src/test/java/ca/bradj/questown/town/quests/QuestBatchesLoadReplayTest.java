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
 * Load-time replay of a completed batch must re-mark the quests complete WITHOUT re-claiming the
 * already-applied batch reward: {@link QuestBatch#markRecipeAsComplete(Object, Object)} claims the
 * reward when the batch closes, which would trip the once-guard on every load of a post-onboarding
 * world.
 * <p>
 * The restored-batch construction mirrors the production deserializer exactly
 * ({@code new MCQuestBatch()} + {@code initialize(batchUUID, allQuests, reward)},
 * {@code MCQuestBatch.SERIALIZER.deserializeNBT}); the NBT byte layer is irrelevant to the claim
 * path, so the test builds the restored objects directly.
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

    // A curated path's "Train a Crafter to level 3" survives a reload.
    @Test
    void loadReplay_keepsAVillagerLevelQuest() {
        MCQuest saved = MCQuest.villagerLevel(UUID.randomUUID(), null, "crafter", 3);

        MCQuest loaded = reloadOne(saved);

        Assertions.assertEquals(Quest.QuestType.VILLAGER_LEVEL, loaded.getType());
        Assertions.assertEquals(saved.getWantedId(), loaded.getWantedId());
    }

    // "Unlock Wooden Shovel" stays a one-skill quest through the typed rebuild.
    @Test
    void loadReplay_keepsANamedSkillQuestsSkill() {
        ca.bradj.questown.jobs.JobID shovel = new ca.bradj.questown.jobs.JobID("crafter", "wooden_shovel");
        MCQuest saved = MCQuest.namedSkillUnlock(UUID.randomUUID(), null, shovel);

        MCQuest loaded = reloadOne(saved);

        Assertions.assertEquals(java.util.Optional.of(shovel), MCQuest.namedSkill(loaded.getWantedId()));
    }

    // The onboarding marker survives a real flag reload, so the arrival gate
    // (TownQuests.isArrivalGateOpen) stays shut until Raw Food.
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
