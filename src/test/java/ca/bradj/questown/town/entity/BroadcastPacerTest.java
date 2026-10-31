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
     * Agent playtest 2026-10-22 r6: removing a bedroom door printed "Bedroom destroyed at
     * location [-44, -35]" twice. The same line fired three times in one tick: the first was
     * sent, the second was queued (the queue held nothing to compare with), the third dropped.
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
