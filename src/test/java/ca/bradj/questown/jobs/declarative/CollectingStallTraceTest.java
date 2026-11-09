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
        Assertions.assertEquals(2, out.size(), out.toString());
        Assertions.assertTrue(out.get(0).startsWith("stuck 1200 ticks"));
    }

    @Test
    void theReportIsFollowedByTheTrailSinceTheLastReset() {
        CollectingStallTrace t = new CollectingStallTrace();
        List<String> trails = new ArrayList<>();
        t.observe(true, false, 0, () -> new double[]{9, 64, 9}, () -> "state", m -> {}, trails::add);
        t.observe(false, false, 1, () -> new double[]{9, 64, 9}, () -> "state", m -> {}, trails::add);
        for (long tick = 2; tick <= 1300; tick++) {
            double x = tick < 50 ? tick / 10.0 : 4.9;
            t.observe(true, false, tick, () -> new double[]{x, 64, 3}, () -> "state", m -> {}, trails::add);
        }
        Assertions.assertEquals(1, trails.size(), trails.toString());
        Assertions.assertFalse(trails.get(0).contains("9.00"), trails.get(0));
        // Reported at tick 1202 (1200 stuck); the ring holds ticks 3..1202, and x reaches 4.9 at tick 49.
        Assertions.assertTrue(trails.get(0).startsWith("from tick 3: +0 (0.30,64.00,3.00)"), trails.get(0));
        Assertions.assertTrue(trails.get(0).endsWith("; +46..+1199 (4.90,64.00,3.00)"), trails.get(0));
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
