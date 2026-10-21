package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class CollectingStallTraceTest {

    private static List<String> run(boolean[] collecting, boolean[] took, long[] ticks) {
        CollectingStallTrace t = new CollectingStallTrace();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ticks.length; i++) {
            t.observe(collecting[i], took[i], ticks[i], () -> "state", out::add);
        }
        return out;
    }

    @Test
    void reportsOnceAfterAMinuteStuck_thenEveryFiveMinutes() {
        CollectingStallTrace t = new CollectingStallTrace();
        List<String> out = new ArrayList<>();
        for (long tick = 0; tick <= 8000; tick += 100) {
            t.observe(true, false, tick, () -> "state", out::add);
        }
        // first at 1200, then at 7200
        Assertions.assertEquals(2, out.size(), out.toString());
        Assertions.assertTrue(out.get(0).startsWith("stuck 1200 ticks"));
    }

    @Test
    void aTakeOrLeavingTheStatusResets() {
        Assertions.assertEquals(List.of(), run(
                new boolean[]{true, true, false, true, true},
                new boolean[]{false, false, false, false, false},
                new long[]{0, 1100, 1150, 1200, 2300}
        ));
        Assertions.assertEquals(List.of(), run(
                new boolean[]{true, true, true},
                new boolean[]{false, true, false},
                new long[]{0, 1100, 2200}
        ));
    }
}
