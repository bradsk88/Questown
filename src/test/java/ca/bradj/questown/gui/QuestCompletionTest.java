package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the "all quests done" empty-state trigger.
 *
 * <p>{@link QuestCompletion#allComplete} is the regression-bearing seam for this
 * feature: the server computes the empty-state booleans from the exact quest list
 * it serializes, so the client view can never disagree with the cards it hides.
 *
 * <p>NOT covered here (or by the in-game autotest suite): the client render of the
 * empty-state and the "View completed quests →" click-through. The autotest suite
 * is server-driven gameplay and never opens a GUI, so it structurally cannot drive
 * the click or read the rendered text. Only pixels/clicks are uncovered — the
 * decision logic is fully exercised below.
 */
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
