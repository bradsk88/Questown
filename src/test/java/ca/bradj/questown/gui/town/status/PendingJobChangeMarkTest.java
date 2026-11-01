package ca.bradj.questown.gui.town.status;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingJobChangeMarkTest {

    @Test
    void pendingJobChange_getsAnAsterisk() {
        // A villager with a pending job change has an asterisk on its job title on the flag's
        // villagers page.
        assertEquals("Gatherer*", PendingJobChangeMark.title("Gatherer", true));
    }

    @Test
    void switchAfterTrip_showsTheJobItBecomes() {
        // "Switch after this trip" gets a note on the villager card until it happens.
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
