package ca.bradj.questown.town;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissedMorningTest {

    @Test
    void awayOvernight_backAtNoon_missedTheMorning() {
        // The playtest: left in the evening of day 2, back at noon of day 3.
        assertTrue(MissedMorning.missedDuringAbsence(61_000, 80_296));
    }

    @Test
    void awayOvernight_backDuringMorning_tickerCatchesIt() {
        assertFalse(MissedMorning.missedDuringAbsence(61_000, 73_000));
    }

    @Test
    void awayWithinOneDay_noDawnCrossed() {
        assertFalse(MissedMorning.missedDuringAbsence(50_000, 60_000));
    }

    @Test
    void leftDuringAMorning_backDuringAMorning_tickerLatchIsStillSet() {
        assertTrue(MissedMorning.missedDuringAbsence(49_000, 73_000));
    }

    @Test
    void noTimePassed() {
        assertFalse(MissedMorning.missedDuringAbsence(80_000, 80_000));
    }
}
