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
    void noPendingChange_plainTitle() {
        assertEquals("Gatherer", PendingJobChangeMark.title("Gatherer", false));
    }
}
