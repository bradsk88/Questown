package ca.bradj.questown.town.quests;

import ca.bradj.questown.core.VillagerUUID;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestOrdinalsTest {

    private static TestQuest room(int id) {
        return TestQuest.standalone(VillagerUUID.from(UUID.randomUUID()), id, Quest.QuestStatus.ACTIVE);
    }

    @Test
    void identicalQuestsAreNumberedInOrder() {
        TestQuest bedroom1 = room(1);
        TestQuest bedroom2 = room(1);
        TestQuest bedroom3 = room(1);
        Map<TestQuest, Integer> ordinals = QuestOrdinals.of(List.of(bedroom1, bedroom2, bedroom3));
        assertEquals(1, ordinals.get(bedroom1));
        assertEquals(2, ordinals.get(bedroom2));
        assertEquals(3, ordinals.get(bedroom3));
    }

    @Test
    void differentQuestsEachStartAtOne() {
        TestQuest bedroom = room(1);
        TestQuest kitchen = room(2);
        TestQuest bedroom2 = room(1);
        Map<TestQuest, Integer> ordinals = QuestOrdinals.of(List.of(bedroom, kitchen, bedroom2));
        assertEquals(1, ordinals.get(bedroom));
        assertEquals(1, ordinals.get(kitchen));
        assertEquals(2, ordinals.get(bedroom2));
    }

    @Test
    void completedQuestsStillCount() {
        TestQuest done = TestQuest.standalone(VillagerUUID.from(UUID.randomUUID()), 1, Quest.QuestStatus.COMPLETED);
        TestQuest open = room(1);
        assertEquals(2, QuestOrdinals.of(List.of(done, open)).get(open));
    }
}
