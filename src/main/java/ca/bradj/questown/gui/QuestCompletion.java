package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;

import java.util.Collection;

/** Whether every quest on the town quests screen is complete. */
public class QuestCompletion {

    private QuestCompletion() {
    }

    public static boolean allComplete(Collection<Quest.QuestStatus> statuses) {
        return !statuses.isEmpty()
                && statuses.stream().allMatch(Quest.QuestStatus.COMPLETED::equals);
    }
}
