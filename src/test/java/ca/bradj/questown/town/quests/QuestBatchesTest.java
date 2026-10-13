package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.UUID;

class QuestBatchesTest {

    static Quest.QuestFactory<Integer, Room, TestQuest> factory = new Quest.QuestFactory<>() {
        @Override
        public TestQuest newQuest(
                @Nullable UUID ownerId, Integer recipeId
        ) {
            return TestQuest.standalone(VillagerUUID.from(ownerId), recipeId, Quest.QuestStatus.ACTIVE);
        }

        @Override
        public TestQuest newUpgradeQuest(
                @Nullable UUID ownerId, Integer oldRecipeId, Integer newRecipeId
        ) {
            return TestQuest.upgrade(VillagerUUID.from(ownerId), newRecipeId, oldRecipeId, Quest.QuestStatus.ACTIVE);
        }

        @Override
        public TestQuest newItemQuest(
                @Nullable UUID ownerId,
                Integer itemId,
                int count
        ) {
            throw new UnsupportedOperationException("Not Implemented");
        }

        @Override
        public TestQuest newLootQuest(
                @Nullable UUID ownerId,
                Integer itemId,
                int count
        ) {
            throw new UnsupportedOperationException("Not Implemented");
        }

        @Override
        public TestQuest newJobQuest(Integer id) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public TestQuest newJobUnlockQuest(Integer id) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public TestQuest newFirstWorkRequestQuest(@Nullable UUID ownerId) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public TestQuest newConcurrentJobsQuest(@Nullable UUID ownerId, Collection<Integer> jobIds) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public TestQuest completed(Room room, TestQuest input) {
            TestQuest testQuest;
            if (input.fromRecipeID().isPresent()) {
                testQuest = TestQuest.upgrade(input.ownerUUID, input.recipeId, input.fromRecipeID().get(), Quest.QuestStatus.COMPLETED);
            } else {
                testQuest = TestQuest.standalone(input.ownerUUID, input.recipeId, Quest.QuestStatus.COMPLETED);
            }
            testQuest.completedOn = room;
            return testQuest;
        }

        @Override
        public TestQuest lost(TestQuest input) {

            TestQuest testQuest;
            if (input.fromRecipeID().isPresent()) {
                testQuest = TestQuest.upgrade(input.ownerUUID, input.recipeId, input.fromRecipeID().get(), Quest.QuestStatus.ACTIVE);
            } else {
                testQuest = TestQuest.standalone(input.ownerUUID, input.recipeId, Quest.QuestStatus.ACTIVE);
            }
            testQuest.completedOn = null;
            return testQuest;
        }
    };

    private static class TestQuestBatch extends QuestBatch<Integer, Room, TestQuest, Reward> {

        TestQuestBatch() {
            super(factory, new Reward() {
                @Override
                public String toNiceString() {
                    return getName() + " (nice)";
                }

                @Override
                protected String getName() {
                    return "Test reward";
                }

                @Override
                protected @NotNull Reward.RewardApplier getApplier() {
                    return () -> {
                    };
                }

                @Override
                public boolean addsQuestsWhenApplied() {
                    return false;
                }
            }, UUID.randomUUID());
        }
    }

    @Test
    void markRecipeAsConvertedShouldRemoveOldCompletedQuestAndCompleteNewQuest_AcrossBatches() {
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((batchUUID, owner, reward) -> new QuestBatch<>(factory, reward, batchUUID));

        Room sameRoom = new Room(
                new Position(1, 2),
                InclusiveSpace.from(3, 4).to(5, 6)
        );

        TestQuestBatch oldBatch = new TestQuestBatch();
        oldBatch.addNewQuest(null, 1);
        oldBatch.markRecipeAsComplete(sameRoom, 1);

        TestQuestBatch newBatch = new TestQuestBatch();
        newBatch.addNewUpgradeQuest(null, 1, 2);

        qbs.add(oldBatch);
        qbs.add(newBatch);

        qbs.markRecipeAsConverted(sameRoom, 1, 2);

        ImmutableList<TestQuest> quests = qbs.getAll();
        Assertions.assertEquals(1, quests.size());
        Assertions.assertEquals(2, quests.get(0).getWantedId());
        Assertions.assertTrue(quests.get(0).isComplete());
    }

    @Test
    void markRecipeAsLost_doesNotLoseAnAlreadyCompleteQuest() {
        // Regression (2026-10-14 playtest, server-18 03:47:37): the empty-room quest
        // completed, then the user placed a chest in the same room and re-registered it
        // (the room re-typed from empty_room to store_room). markRecipeAsLost found the
        // COMPLETED quest via completedOn and lost it - the player saw "Quest lost: Empty
        // Room" on a perma-done quest, and the lost placeholder re-completed in the same
        // tick, double-firing the batch reward. Complete quests are perma-done
        // (campfire semantics); room re-typing must not touch them.
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((batchUUID, owner, reward) -> new QuestBatch<>(factory, reward, batchUUID));

        Room room = new Room(
                new Position(1, 2),
                InclusiveSpace.from(3, 4).to(5, 6)
        );

        TestQuestBatch batch = new TestQuestBatch();
        batch.addNewQuest(null, 1);
        batch.markRecipeAsComplete(room, 1);

        qbs.add(batch);

        boolean lost = batch.markRecipeAsLost(room, 1);

        Assertions.assertFalse(lost);
        ImmutableList<TestQuest> quests = batch.getAll();
        Assertions.assertEquals(1, quests.size());
        Assertions.assertTrue(quests.get(0).isComplete());
    }

    @Test
    void markRecipeAsComplete_completesOpenQuest_evenWhenSameRoomAndRecipeWereCompletedBefore() {
        // Regression (2026-10-10, kitchen_stocked wall): the 96a74471 startup
        // double-fill guard returned whenever ANY quest for this recipe had been
        // completed on this room. Room objects keep their identity across rescans,
        // so a re-issued quest for the same recipe (visitor-batch re-issue after the
        // batch-completion reward) was bounced by the original completion forever
        // and the chain stalled. The open quest must complete.
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((batchUUID, i, r) -> new QuestBatch<>(factory, r, batchUUID));

        Room sameRoom = new Room(
                new Position(1, 2),
                InclusiveSpace.from(3, 4).to(5, 6)
        );

        TestQuestBatch incompleteBatch = new TestQuestBatch();
        incompleteBatch.addNewQuest(null, 1);

        TestQuestBatch completeBatch = new TestQuestBatch();
        completeBatch.addNewQuest(null, 1);
        completeBatch.markRecipeAsComplete(sameRoom, 1);

        qbs.add(incompleteBatch);
        qbs.add(completeBatch);

        qbs.markRecipeAsComplete(sameRoom, 1);

        ImmutableList<TestQuest> quests = qbs.getAll();
        Assertions.assertEquals(2, quests.size());
        Assertions.assertEquals(2, quests.stream()
                .filter(Quest::isComplete)
                .filter(v -> v.getWantedId().equals(1))
                .filter(v -> sameRoom.equals(v.completedOn))
                .count()
        );
        Assertions.assertEquals(0, quests.stream()
                .filter(Predicates.not(Quest::isComplete))
                .filter(v -> v.getWantedId().equals(1))
                .count()
        );
    }

    @Test
    void markRecipeAsComplete_hasNoEffect_whenNoOpenQuestRemainsForRecipe() {
        // The 96a74471 intent that survives: re-announcing a room recipe (e.g. on
        // server start) must not re-mark or re-reward when every quest for that
        // recipe is already complete.
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((batchUUID, i, r) -> new QuestBatch<>(factory, r, batchUUID));

        Room sameRoom = new Room(
                new Position(1, 2),
                InclusiveSpace.from(3, 4).to(5, 6)
        );

        TestQuestBatch completeBatch = new TestQuestBatch();
        completeBatch.addNewQuest(null, 1);
        completeBatch.markRecipeAsComplete(sameRoom, 1);

        qbs.add(completeBatch);

        qbs.markRecipeAsComplete(sameRoom, 1);

        ImmutableList<TestQuest> quests = qbs.getAll();
        Assertions.assertEquals(1, quests.size());
        Assertions.assertTrue(quests.get(0).isComplete());
        Assertions.assertSame(sameRoom, quests.get(0).completedOn);
    }

    @Test
    void initialize_shouldRemoveDuplicateCompletion_IfRoomAndRecipeAreSame() {
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((batchUUID, i, r) -> new QuestBatch<>(factory, r, batchUUID));

        Room sameRoom = new Room(
                new Position(1, 2),
                InclusiveSpace.from(3, 4).to(5, 6)
        );

        TestQuestBatch batch = new TestQuestBatch();
        batch.addNewQuest(null, 1);
        batch.markRecipeAsComplete(sameRoom, 1);
        batch.addNewQuest(null, 1);
        batch.markRecipeAsComplete(sameRoom, 1);

        ImmutableList.Builder<QuestBatch<Integer, Room, TestQuest, Reward>> iqb = ImmutableList.builder();
        iqb.add(batch);

        qbs.initialize(new QuestBatches.VillagerProvider<>() {
            @Override
            public UUID getRandomVillager() {
                return UUID.randomUUID();
            }

            @Override
            public boolean isVillagerMissing(UUID uuid) {
                return false;
            }

        }, iqb.build());

        ImmutableList<TestQuest> quests = qbs.getAll();
        Assertions.assertEquals(2, quests.size());
        Assertions.assertEquals(1, quests.stream()
                .filter(Quest::isComplete)
                .filter(v -> v.getWantedId().equals(1))
                .filter(v -> sameRoom.equals(v.completedOn))
                .count()
        );
        Assertions.assertEquals(1, quests.stream()
                .filter(Predicates.not(Quest::isComplete))
                .filter(v -> v.getWantedId().equals(1))
                .filter(v -> v.completedOn == null)
                .count()
        );
    }
}