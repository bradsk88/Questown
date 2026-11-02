package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import org.jetbrains.annotations.Nullable;

/** The "(n/m)" on an item or loot quest card: the live count while open, the target once done. */
public final class QuestCardProgress {

    private QuestCardProgress() {
    }

    public static @Nullable Integer shown(
            Quest.QuestType type,
            Quest.QuestStatus status,
            int countNeeded,
            @Nullable Integer live
    ) {
        if (type != Quest.QuestType.ITEM && type != Quest.QuestType.LOOT && type != Quest.QuestType.VILLAGER_LEVEL) {
            return null;
        }
        if (status == Quest.QuestStatus.COMPLETED) {
            return countNeeded;
        }
        return live;
    }
}
