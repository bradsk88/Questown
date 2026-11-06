package ca.bradj.questown.integration.minecraft;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class ChestLidTimersTest {

    @Test
    void firstTouch_opens_thenClosesAfterTheOpenTime() {
        ChestLidTimers<String> t = new ChestLidTimers<>();
        Assertions.assertTrue(t.touch("chest", 100));
        Assertions.assertEquals(List.of(), t.due(100 + ChestLidTimers.OPEN_TICKS - 1));
        Assertions.assertEquals(List.of("chest"), t.due(100 + ChestLidTimers.OPEN_TICKS));
        Assertions.assertEquals(List.of(), t.due(1000));
    }

    @Test
    void touchesWhileOpen_keepTheLidUp_withoutReopening() {
        ChestLidTimers<String> t = new ChestLidTimers<>();
        t.touch("chest", 100);
        Assertions.assertFalse(t.touch("chest", 105));
        Assertions.assertEquals(List.of(), t.due(110));
        Assertions.assertEquals(List.of("chest"), t.due(105 + ChestLidTimers.OPEN_TICKS));
    }

    @Test
    void afterClosing_aTouchOpensAgain() {
        ChestLidTimers<String> t = new ChestLidTimers<>();
        t.touch("chest", 100);
        t.due(200);
        Assertions.assertTrue(t.touch("chest", 201));
    }
}
