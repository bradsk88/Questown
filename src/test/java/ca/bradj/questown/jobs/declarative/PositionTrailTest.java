package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PositionTrailTest {

    @Test
    void standingStillIsOneRun_eachMoveStartsANewOne() {
        PositionTrail t = new PositionTrail(100);
        t.record(10, 0.5, 64, 0.5);
        t.record(11, 0.5, 64, 0.5);
        t.record(12, 0.504, 64, 0.5);
        t.record(13, 0.7, 64, 0.6);
        t.record(14, 0.9, 64, 0.7);
        t.record(15, 0.9, 64, 0.7);
        Assertions.assertEquals(
                "from tick 10: +0..+2 (0.50,64.00,0.50); +3 (0.70,64.00,0.60); +4..+5 (0.90,64.00,0.70)",
                t.toString()
        );
    }

    @Test
    void keepsOnlyTheLastSamples() {
        PositionTrail t = new PositionTrail(3);
        for (int i = 0; i < 5; i++) {
            t.record(i, i, 64, 0);
        }
        Assertions.assertEquals(
                "from tick 2: +0 (2.00,64.00,0.00); +1 (3.00,64.00,0.00); +2 (4.00,64.00,0.00)",
                t.toString()
        );
    }

    @Test
    void clearEmpties() {
        PositionTrail t = new PositionTrail(3);
        t.record(1, 0, 64, 0);
        t.clear();
        Assertions.assertTrue(t.isEmpty());
        Assertions.assertEquals("empty", t.toString());
    }
}
