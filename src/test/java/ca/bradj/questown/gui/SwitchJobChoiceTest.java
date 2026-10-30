package ca.bradj.questown.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/** Dev playtest 2026-10-29: switching mid-trip offers "after this trip" or "now (trip is lost)". */
class SwitchJobChoiceTest {

    @Test
    void awayOnATrip_offersAfterTheTripOrNow() {
        Assertions.assertEquals(
                List.of(SwitchJobChoice.AFTER_TRIP, SwitchJobChoice.NOW_LOSE_TRIP, SwitchJobChoice.CANCEL),
                SwitchJobChoice.forTrip(true));
    }

    @Test
    void atHome_offersNowOrCancel() {
        Assertions.assertEquals(List.of(SwitchJobChoice.NOW, SwitchJobChoice.CANCEL), SwitchJobChoice.forTrip(false));
    }
}
