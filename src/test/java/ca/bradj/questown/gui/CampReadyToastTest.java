package ca.bradj.questown.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampReadyToastTest {

    @Test
    void staysUpInTheEveningAndAtNight() {
        assertFalse(CampReadyToast.hideNow(true, 12500));
        assertFalse(CampReadyToast.hideNow(true, 18000));
        assertFalse(CampReadyToast.hideNow(true, 23000));
    }

    @Test
    void hidesOnceDaylightComes_onLaterDaysToo() {
        assertTrue(CampReadyToast.hideNow(true, 24000 + 100));
        assertTrue(CampReadyToast.hideNow(true, 24000 * 3 + 7000));
    }

    @Test
    void otherTutorialToastsAreLeftAlone() {
        assertFalse(CampReadyToast.hideNow(false, 1000));
    }
}
