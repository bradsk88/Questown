package ca.bradj.questown.town.quests;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestBatchesTest {

    private static class TestQuestBatch extends QuestBatch<Integer, Room, TestQuest, Reward> {

        TestQuestBatch() {
            super(new Quest.QuestFactory<>() {
                @Override
                public TestQuest newQuest(Integer recipeId) {
                    return TestQuest.standalone(recipeId, Quest.QuestStatus.ACTIVE);
                }

                @Override
                public TestQuest newUpgradeQuest(Integer oldRecipeId, Integer newRecipeId) {
                    return TestQuest.upgrade(newRecipeId, oldRecipeId, Quest.QuestStatus.ACTIVE);
                }

                @Override
                public TestQuest completed(Room room, TestQuest input) {
                    TestQuest testQuest;
                    if (input.fromRecipeID().isPresent()) {
                        testQuest = TestQuest.upgrade(input.recipeId, input.fromRecipeID().get(), Quest.QuestStatus.COMPLETED);
                    } else {
                        testQuest = TestQuest.standalone(input.recipeId, Quest.QuestStatus.COMPLETED);
                    }
                    testQuest.uuid = input.uuid;
                    testQuest.completedOn = room;
                    return testQuest;
                }
            }, new Reward() {
                @Override
                protected String getName() {
                    return "Test reward";
                }

                @Override
                protected @NotNull RewardApplier getApplier() {
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
                > qbs = new QuestBatches<>();

        Room sameRoom = new Room(
                new Position(1, 2),
                new InclusiveSpace(
                        new Position(3, 4),
                        new Position(5, 6)
                )
        );

        TestQuestBatch oldBatch = new TestQuestBatch();
        oldBatch.addNewQuest(1);
        oldBatch.markRecipeAsComplete(sameRoom, 1);

        TestQuestBatch newBatch = new TestQuestBatch();
        newBatch.addNewUpgradeQuest(1, 2);

        qbs.add(oldBatch);
        qbs.add(newBatch);

        qbs.markRecipeAsConverted(sameRoom, 1, 2);

        ImmutableList<TestQuest> quests = qbs.getAll();
        Assertions.assertEquals(1, quests.size());
        Assertions.assertEquals(2, quests.get(0).getWantedId());
        Assertions.assertTrue(quests.get(0).isComplete());
    }
}