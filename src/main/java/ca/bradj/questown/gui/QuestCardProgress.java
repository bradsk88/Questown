package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import org.jetbrains.annotations.Nullable;

/**
 * The "(n/m)" progress an item or loot quest card shows: the live count while open, the count at
 * completion once done. The live count is the town-wide tally for the item, so a completed card
 * otherwise drifts with the storeroom ("Coal x8 (7/8)" on a ticked card, 2026-10-22 playtest).
 * These quests complete only on reaching their count, so the count at completion is the target.
 */
public final class QuestCardProgress {

    private QuestCardProgress() {
    }

    public static @Nullable Integer shown(
            Quest.QuestType type,
            Quest.QuestStatus status,
            int countNeeded,
            @Nullable Integer live
    ) {
        if (type != Quest.QuestType.ITEM && type != Quest.QuestType.LOOT) {
            return null;
        }
        if (status == Quest.QuestStatus.COMPLETED) {
            return countNeeded;
        }
        return live;
    }
}
