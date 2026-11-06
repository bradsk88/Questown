package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link QuestCompletion#allComplete}, which decides the all-done state from the quests the server sends. */
class QuestCompletionTest {

    @Test
    void allComplete_isFalse_whenNoQuests() {
        assertFalse(QuestCompletion.allComplete(ImmutableList.of()));
    }

    @Test
    void allComplete_isFalse_whenAnyQuestIncomplete() {
        assertFalse(QuestCompletion.allComplete(ImmutableList.of(
                Quest.QuestStatus.ACTIVE,
                Quest.QuestStatus.COMPLETED
        )));
    }

    @Test
    void allComplete_isTrue_whenEveryQuestCompleted() {
        assertTrue(QuestCompletion.allComplete(ImmutableList.of(
                Quest.QuestStatus.COMPLETED,
                Quest.QuestStatus.COMPLETED
        )));
    }
}
