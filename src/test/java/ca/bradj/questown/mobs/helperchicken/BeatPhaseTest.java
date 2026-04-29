package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BeatPhaseTest {

    @Test
    void enumHasExpectedValuesInExpectedOrder() {
        BeatPhase[] values = BeatPhase.values();
        assertEquals(6, values.length);
        assertEquals(BeatPhase.DEFAULT, values[0]);
        assertEquals(BeatPhase.NEED_TO_FETCH, values[1]);
        assertEquals(BeatPhase.READY_TO_USE, values[2]);
        assertEquals(BeatPhase.READY_TO_PLACE, values[3]);
        assertEquals(BeatPhase.PREPARING, values[4]);
        assertEquals(BeatPhase.AWAITING_NIGHT, values[5]);
    }

    @Test
    void valueOfRoundTripsExpectedNames() {
        for (BeatPhase phase : BeatPhase.values()) {
            assertNotNull(BeatPhase.valueOf(phase.name()));
        }
    }
}
