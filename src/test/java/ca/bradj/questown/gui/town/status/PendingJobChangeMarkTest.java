package ca.bradj.questown.gui.town.status;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingJobChangeMarkTest {

    @Test
    void pendingJobChange_getsAnAsterisk() {
        assertEquals("Gatherer*", PendingJobChangeMark.title("Gatherer", true));
    }

    @Test
    void switchAfterTrip_showsTheJobItBecomes() {
        assertEquals("Hunter \u2192 Cook", PendingJobChangeMark.afterTripTitle("Hunter", "Cook"));
    }

    @Test
    void switchAfterTrip_shortFormKeepsTheNewJob() {
        assertEquals("\u2192 Blacksmith", PendingJobChangeMark.afterTripTitleShort("Blacksmith"));
    }

    @Test
    void noPendingChange_plainTitle() {
        assertEquals("Gatherer", PendingJobChangeMark.title("Gatherer", false));
    }
}
