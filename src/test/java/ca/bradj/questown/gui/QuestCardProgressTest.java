package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// Dev ruling 2026-10-22: a completed quest card shows its progress frozen at completion,
// not the live storeroom tally ("Coal x8 (7/8)" on a ticked card after the cook burned one).
class QuestCardProgressTest {

    @Test
    void anOpenItemQuestShowsTheLiveCount() {
        Assertions.assertEquals(7, QuestCardProgress.shown(Quest.QuestType.ITEM, Quest.QuestStatus.ACTIVE, 8, 7));
    }

    @Test
    void aCompletedItemQuestShowsItsCountAtCompletion() {
        Assertions.assertEquals(8, QuestCardProgress.shown(Quest.QuestType.ITEM, Quest.QuestStatus.COMPLETED, 8, 7));
    }

    @Test
    void aCompletedLootQuestShowsItsCountAtCompletion() {
        Assertions.assertEquals(8, QuestCardProgress.shown(Quest.QuestType.LOOT, Quest.QuestStatus.COMPLETED, 8, 3));
    }

    @Test
    void otherQuestTypesShowNoCount() {
        Assertions.assertNull(QuestCardProgress.shown(Quest.QuestType.ROOM, Quest.QuestStatus.ACTIVE, 1, 5));
    }
}
