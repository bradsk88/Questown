package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;

import java.util.Collection;

/**
 * Pure completion predicate for the town quests screen. Operates on plain
 * {@link Quest.QuestStatus} values (no MC registry deps) so the empty-state
 * trigger can be unit-tested without Bootstrap/Ingredient construction.
 */
public class QuestCompletion {

    private QuestCompletion() {
    }

    public static boolean allComplete(Collection<Quest.QuestStatus> statuses) {
        return !statuses.isEmpty()
                && statuses.stream().allMatch(Quest.QuestStatus.COMPLETED::equals);
    }
}
