package ca.bradj.questown.gui;

/** The quests screen's summary: whether all quests are done, and whether a morning reward is waiting. */
public record QuestScreenSummary(
        boolean allComplete,
        boolean pendingReward
) {
    public static final QuestScreenSummary NONE = new QuestScreenSummary(false, false);
}
