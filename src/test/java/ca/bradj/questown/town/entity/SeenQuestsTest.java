package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class SeenQuestsTest {

    @Test
    void openQuests_areUnseenUntilTheTabIsOpened() {
        SeenQuests s = new SeenQuests();
        Assertions.assertTrue(s.hasUnseen(List.of("b1|bedroom")));
        s.markSeen(List.of("b1|bedroom"));
        Assertions.assertFalse(s.hasUnseen(List.of("b1|bedroom")));
    }

    @Test
    void aNewQuest_isUnseenAgain() {
        SeenQuests s = new SeenQuests();
        s.markSeen(List.of("b1|bedroom"));
        Assertions.assertTrue(s.hasUnseen(List.of("b1|bedroom", "b2|kitchen")));
    }

    @Test
    void completingASeenQuest_doesNotRedDot() {
        SeenQuests s = new SeenQuests();
        s.markSeen(List.of("b1|bedroom", "b1|swords"));
        Assertions.assertFalse(s.hasUnseen(List.of("b1|swords")));
        Assertions.assertFalse(s.hasUnseen(List.of()));
    }
}
