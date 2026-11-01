package ca.bradj.questown.gui;

/**
 * Server-computed summary of the town quests screen, synced as two booleans
 * alongside the existing town-quests data. {@code allComplete} drives the
 * empty-state view; {@code pendingReward} chooses the morning vs caught-up line.
 */
public record QuestScreenSummary(
        boolean allComplete,
        boolean pendingReward
) {
    public static final QuestScreenSummary NONE = new QuestScreenSummary(false, false);
}
