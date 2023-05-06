package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuestsTest {

    private QuestBatch<Integer, TestQuest, Reward> quests;

    @BeforeEach
    void setUp() {
        quests = new QuestBatch<>(new Quest.QuestFactory<>() {
            @Override
            public TestQuest newQuest(Integer recipeId) {
                return new TestQuest(recipeId);
            }

            @Override
            public TestQuest withStatus(
                    TestQuest input,
                    Quest.QuestStatus status
            ) {
                TestQuest testQuest = new TestQuest(input.recipeId, status);
                testQuest.uuid = input.uuid;
                return testQuest;
            }
        }, new Reward(() -> {
        }) {
            @Override
            protected String getName() {
                return "Test reward";
            }
        });
    }

    @Test
    void getCompleted_returnsEmptyCollection_whenNoQuestsCompleted() {
        quests.addNewQuest(1);
        quests.addNewQuest(2);

        assertTrue(quests.getCompleted().isEmpty());
    }

    @Test
    void getCompleted_returnsCollectionOfCompletedQuestIds() {
        quests.addNewQuest(1);
        quests.addNewQuest(2);

        quests.markRecipeAsComplete(1);
        quests.markRecipeAsComplete(2);

        assertEquals(List.of(1, 2), quests.getCompleted());
    }

    @Test
    void addNewQuest_addsNewQuestToQuests() {
        quests.addNewQuest(1);

        assertEquals(1, quests.getAll().size());
        assertEquals(1, quests.getAll().get(0).getId());
    }

    @Test
    void getAll_returnsImmutableList() {
        quests.addNewQuest(1);
        ImmutableList<TestQuest> allQuests = quests.getAll();

        assertThrows(UnsupportedOperationException.class, () -> allQuests.add(new TestQuest(2)));
    }

    @Test
    void initialize_throwsException_whenQuestsAlreadyInitialized() {
        quests.addNewQuest(1);
        assertThrows(IllegalStateException.class, () -> quests.initialize(ImmutableList.of(), null));
    }

    @Test
    void markRecipeAsComplete_doesNothing_whenNoMatchingIncompleteQuests() {
        quests.addNewQuest(1);
        quests.markRecipeAsComplete(1);

        assertEquals(List.of(1), quests.getCompleted());
    }

    @Test
    void markRecipeAsComplete_marksMatchingIncompleteQuestAsCompleted() {
        quests.addNewQuest(1);
        quests.addNewQuest(2);

        quests.markRecipeAsComplete(1);

        Optional<TestQuest> completedQuest = quests.getAll().stream().filter(v -> v.getId() == 1).findFirst();
        assertTrue(completedQuest.isPresent());
        assertEquals(Quest.QuestStatus.COMPLETED, completedQuest.get().getStatus());
        assertEquals(List.of(1), quests.getCompleted());
    }

    @Test
    void markRecipeAsComplete_notifiesChangeListener() {
        quests.addNewQuest(1);

        TestQuest completedQuest = quests.getAll().get(0);
        QuestBatch.ChangeListener<TestQuest> listener = new QuestBatch.ChangeListener<TestQuest>() {

            @Override
            public void questCompleted(TestQuest q) {

                assertEquals(completedQuest.getUUID(), q.getUUID());
                assertEquals(completedQuest.getId(), q.getId());
                assertEquals(Quest.QuestStatus.COMPLETED, q.getStatus());
            }

            @Override
            public void questBatchCompleted(QuestBatch<?, ?, ?> quest) {

            }
        };
        quests.addChangeListener(listener);

        quests.markRecipeAsComplete(1);
    }
}

class TestQuest extends Quest<Integer> {

    TestQuest(Integer id) {
        super(id);
    }

    TestQuest(
            Integer id,
            QuestStatus status
    ) {
        super(id);
        super.status = status;
    }

    @Override
    public String toString() {
        return "TestQuest{" +
                "uuid=" + uuid +
                ", recipeId=" + recipeId +
                ", status=" + status +
                '}';
    }
}