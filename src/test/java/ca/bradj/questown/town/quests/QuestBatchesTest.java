package ca.bradj.questown.town.quests;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class QuestBatchesTest {

    static Quest.QuestFactory<Integer, Room, TestQuest> factory = new Quest.QuestFactory<>() {
        @Override
        public TestQuest newQuest(
                @Nullable UUID ownerId, Integer recipeId
        ) {
            return TestQuest.standalone(ownerId, recipeId, Quest.QuestStatus.ACTIVE);
        }

        @Override
        public TestQuest newUpgradeQuest(
                @Nullable UUID ownerId, Integer oldRecipeId, Integer newRecipeId
        ) {
            return TestQuest.upgrade(ownerId, newRecipeId, oldRecipeId, Quest.QuestStatus.ACTIVE);
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
                protected String getName() {
                    return "Test reward";
                }

                @Override
                protected @NotNull Reward.RewardApplier getApplier() {
                    return () -> {
                    };
                }
            });
        }
    }

    @Test
    void markRecipeAsConvertedShouldRemoveOldCompletedQuestAndCompleteNewQuest_AcrossBatches() {
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((owner, reward) -> new QuestBatch<>(factory, reward));

        Room sameRoom = new Room(
                new Position(1, 2),
                new InclusiveSpace(
                        new Position(3, 4),
                        new Position(5, 6)
                )
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
    void markRecipeAsComplete_ShouldHaveNoEffect_IfRoomAndRecipeAreAlreadyPresent() {
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((i, r) -> new QuestBatch<>(factory, r));

        Room sameRoom = new Room(
                new Position(1, 2),
                new InclusiveSpace(
                        new Position(3, 4),
                        new Position(5, 6)
                )
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

    @Test
    void initialize_shouldRemoveDuplicateCompletion_IfRoomAndRecipeAreSame() {
        QuestBatches<
                Integer, Room, TestQuest, Reward,
                QuestBatch<Integer, Room, TestQuest, Reward>
                > qbs = new QuestBatches<>((i, r) -> new QuestBatch<>(factory, r));

        Room sameRoom = new Room(
                new Position(1, 2),
                new InclusiveSpace(
                        new Position(3, 4),
                        new Position(5, 6)
                )
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

            @Override
            public Optional<Room> assignToFarm(UUID ownerUUID) {
                return Optional.empty();
            }

            @Override
            public Optional<Room> getBiggestFarm() {
                return Optional.empty();
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