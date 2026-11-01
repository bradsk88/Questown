package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class BroadcastPacerTest {

    @Test
    void aBurstIsSpacedOut() {
        List<String> sent = new ArrayList<>();
        BroadcastPacer<String> p = new BroadcastPacer<>(100);

        p.offer("a", 0, sent::add);
        p.offer("b", 0, sent::add);
        p.flush(50, sent::add);
        Assertions.assertEquals(List.of("a"), sent);
        p.flush(100, sent::add);
        Assertions.assertEquals(List.of("a", "b"), sent);
    }

    /**
     * The same line three times in one tick: the first is sent, and the duplicates are dropped
     * (the queue must compare against what it sent, not only what it holds).
     */
    @Test
    void aDuplicateOfTheLineJustSent_isDropped() {
        List<String> sent = new ArrayList<>();
        BroadcastPacer<String> p = new BroadcastPacer<>(100);

        p.offer("destroyed", 0, sent::add);
        Assertions.assertEquals(BroadcastPacer.Outcome.DROPPED, p.offer("destroyed", 0, sent::add));
        p.flush(100, sent::add);
        Assertions.assertEquals(List.of("destroyed"), sent);
    }

    @Test
    void theSameLineLaterOn_isSentAgain() {
        List<String> sent = new ArrayList<>();
        BroadcastPacer<String> p = new BroadcastPacer<>(100);

        p.offer("destroyed", 0, sent::add);
        p.offer("destroyed", 500, sent::add);
        Assertions.assertEquals(List.of("destroyed", "destroyed"), sent);
    }
}
