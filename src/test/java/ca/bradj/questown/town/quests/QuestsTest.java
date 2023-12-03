package ca.bradj.questown.town.quests;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestsTest {

    private static final Room testRoom1 = new Room(
            new Position(1, 2),
            new InclusiveSpace(new Position(3, 4), new Position(5, 6))
    );
    private static final Room testRoom2 = new Room(
            new Position(7, 8),
            new InclusiveSpace(new Position(9, 10), new Position(11, 12))
    );

    private QuestBatch<Integer, Room, TestQuest, Reward> quests;

    @BeforeEach
    void setUp() {
        quests = new QuestBatch<>(new Quest.QuestFactory<>() {
            @Override
            public TestQuest newQuest(@Nullable UUID ownerId, Integer recipeId) {
                return TestQuest.standalone(ownerId, recipeId, Quest.QuestStatus.ACTIVE);
            }

            @Override
            public TestQuest newUpgradeQuest(@Nullable UUID ownerId, Integer oldRecipeId, Integer newRecipeId) {
                return TestQuest.upgrade(ownerId, newRecipeId, oldRecipeId, Quest.QuestStatus.ACTIVE);
            }

            @Override
            public TestQuest completed(Room room, TestQuest input) {
                TestQuest testQuest;
                if (input.fromRecipeID().isPresent()) {
                    testQuest = TestQuest.upgrade(null, input.recipeId, input.fromRecipeID().get(), Quest.QuestStatus.COMPLETED);
                } else {
                    testQuest = TestQuest.standalone(null, input.recipeId, Quest.QuestStatus.COMPLETED);
                }
                testQuest.ownerUUID = input.ownerUUID;
                return testQuest;
            }

            @Override
            public TestQuest lost(TestQuest input) {
                TestQuest testQuest;
                if (input.fromRecipeID().isPresent()) {
                    testQuest = TestQuest.upgrade(null, input.recipeId, input.fromRecipeID().get(), Quest.QuestStatus.ACTIVE);
                } else {
                    testQuest = TestQuest.standalone(null, input.recipeId, Quest.QuestStatus.ACTIVE);
                }
                testQuest.ownerUUID = input.ownerUUID;
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
        }, null);
    }

    @Test
    void getCompleted_returnsEmptyCollection_whenNoQuestsCompleted() {
        quests.addNewQuest(null, 1);
        quests.addNewQuest(null, 2);

        assertTrue(quests.getCompletedRecipeIDs().isEmpty());
    }

    @Test
    void getCompleted_returnsCollectionOfCompletedQuestIds() {
        quests.addNewQuest(null, 1);
        quests.addNewQuest(null, 2);

        quests.markRecipeAsComplete(testRoom1, 1);
        quests.markRecipeAsComplete(testRoom2, 2);

        assertEquals(List.of(1, 2), quests.getCompletedRecipeIDs());
    }

    @Test
    void addNewQuest_addsNewQuestToQuests() {
        quests.addNewQuest(null, 1);

        assertEquals(1, quests.getAll().size());
        assertEquals(1, quests.getAll().get(0).getWantedId());
    }

    @Test
    void getAll_returnsImmutableList() {
        quests.addNewQuest(null, 1);
        ImmutableList<TestQuest> allQuests = quests.getAll();

        assertThrows(
                UnsupportedOperationException.class,
                () -> allQuests.add(TestQuest.standalone(null, 2, Quest.QuestStatus.ACTIVE))
        );
    }

    @Test
    void initialize_throwsException_whenQuestsAlreadyInitialized() {
        quests.addNewQuest(null, 1);
        assertThrows(IllegalStateException.class, () -> quests.initialize(null, ImmutableList.of(), null));
    }

    @Test
    void markRecipeAsComplete_doesNothing_whenNoMatchingIncompleteQuests() {
        quests.addNewQuest(null, 1);
        quests.markRecipeAsComplete(testRoom1, 1);

        assertEquals(List.of(1), quests.getCompletedRecipeIDs());
    }

    @Test
    void markRecipeAsComplete_marksMatchingIncompleteQuestAsCompleted() {
        quests.addNewQuest(null, 1);
        quests.addNewQuest(null, 2);

        quests.markRecipeAsComplete(testRoom1, 1);

        Optional<TestQuest> completedQuest = quests.getAll().stream().filter(v -> v.getWantedId() == 1).findFirst();
        assertTrue(completedQuest.isPresent());
        assertEquals(Quest.QuestStatus.COMPLETED, completedQuest.get().getStatus());
        assertEquals(List.of(1), quests.getCompletedRecipeIDs());
    }

    @Test
    void markRecipeAsComplete_removesMatchingCompletedQuestWhenCompletingUpgradeQuest() {
        quests.addNewQuest(null, 1);
        quests.addNewUpgradeQuest(null, 1, 2);

        quests.markRecipeAsComplete(testRoom1, 1);
        quests.markRecipeAsConverted(testRoom1, 1, 2);

        List<TestQuest> converted = quests.getAll()
                .stream()
                .filter(v -> v.getWantedId() == 2)
                .toList();
        assertEquals(1, converted.size());
        assertEquals(Quest.QuestStatus.COMPLETED, converted.get(0).getStatus());
        assertEquals(List.of(1, 2), quests.getCompletedRecipeIDs());
    }

    @Test
    void markRecipeAsComplete_notifiesChangeListener() {
        quests.addNewQuest(null, 1);

        TestQuest completedQuest = quests.getAll().get(0);
        QuestBatch.ChangeListener<TestQuest> listener = new QuestBatch.ChangeListener<TestQuest>() {

            @Override
            public void questCompleted(TestQuest q) {

                assertEquals(completedQuest.getUUID(), q.getUUID());
                assertEquals(completedQuest.getWantedId(), q.getWantedId());
                assertEquals(Quest.QuestStatus.COMPLETED, q.getStatus());
            }

            @Override
            public void questBatchCompleted(QuestBatch<?, ?, ?, ?> quest) {

            }

            @Override
            public void questLost(TestQuest quest) {

            }
        };
        quests.addChangeListener(listener);

        quests.markRecipeAsComplete(testRoom1, 1);
    }
}

class TestQuest extends Quest<Integer, Room> {

    TestQuest(UUID ownerId, Integer id, @Nullable Integer from) {
        super(null, ownerId, id, from);
    }

    public static TestQuest standalone(UUID ownerId, Integer id, QuestStatus status) {
        TestQuest testQuest = new TestQuest(ownerId, id, null);
        testQuest.status = status;
        return testQuest;
    }

    public static TestQuest upgrade(UUID ownerId, Integer id, Integer from, QuestStatus status) {
        TestQuest testQuest = new TestQuest(ownerId, id, from);
        testQuest.status = status;
        return testQuest;
    }

    @Override
    public String toString() {
        return "TestQuest{" +
                "uuid=" + ownerUUID +
                ", recipeId=" + recipeId +
                ", status=" + status +
                ", completedOn=" + completedOn +
                ", from=" + fromRecipeID() +
                '}';
    }



}