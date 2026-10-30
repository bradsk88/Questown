package ca.bradj.questown.gui.town.status;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingJobChangeMarkTest {

    @Test
    void pendingJobChange_getsAnAsterisk() {
        // 2026-10-19 playtest: "If a villager has a pending job change, their job title should
        // have an asterisk on the flag UI's villagers page".
        assertEquals("Gatherer*", PendingJobChangeMark.title("Gatherer", true));
    }

    @Test
    void switchAfterTrip_showsTheJobItBecomes() {
        // Dev 2026-10-30: "Switch after this trip" gets a note on the villager card until it happens.
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
