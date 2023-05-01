package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class QuestsTest {

    private Quests<Integer, TestQuest> quests;

    @BeforeEach
    void setUp() {
        quests = new Quests<Integer, TestQuest>(new Quest.QuestFactory<Integer, TestQuest>() {
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
        assertThrows(IllegalStateException.class, () -> quests.initialize(ImmutableList.of()));
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
        Quests.ChangeListener<TestQuest> listener = q -> {
            assertEquals(completedQuest.getUUID(), q.getUUID());
            assertEquals(completedQuest.getId(), q.getId());
            assertEquals(Quest.QuestStatus.COMPLETED, q.getStatus());
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