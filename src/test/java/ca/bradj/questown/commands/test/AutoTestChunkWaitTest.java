package ca.bradj.questown.commands.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class AutoTestChunkWaitTest {

    @Test
    void holdsUntilTicking_thenLogsHowLongItWaited() {
        AutoTestChunkWait w = new AutoTestChunkWait(60_000);
        List<String> info = new ArrayList<>();
        List<String> error = new ArrayList<>();
        Assertions.assertFalse(w.poll(false, 1000, info::add, error::add));
        Assertions.assertFalse(w.poll(false, 2000, info::add, error::add));
        Assertions.assertTrue(w.poll(true, 4000, info::add, error::add));
        Assertions.assertTrue(w.isDone());
        Assertions.assertEquals(List.of("Force-loaded chunks ticking after 2 ticks (3000 ms)"), info);
        Assertions.assertEquals(List.of(), error);
    }

    @Test
    void manyTicksAreNotATimeout_onlyWallClockIs() {
        AutoTestChunkWait w = new AutoTestChunkWait(60_000);
        List<String> error = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            Assertions.assertFalse(w.poll(false, 1000 + i, s -> {}, error::add));
        }
        Assertions.assertTrue(w.poll(false, 61_000, s -> {}, error::add));
        Assertions.assertEquals(1, error.size(), error.toString());
        Assertions.assertTrue(error.get(0).endsWith("(60000 ms); starting anyway"), error.get(0));
    }

    @Test
    void alreadyTickingStartsAtOnce() {
        AutoTestChunkWait w = new AutoTestChunkWait(60_000);
        List<String> info = new ArrayList<>();
        Assertions.assertTrue(w.poll(true, 5, info::add, s -> {}));
        Assertions.assertEquals(List.of("Force-loaded chunks ticking after 0 ticks (0 ms)"), info);
    }
}
