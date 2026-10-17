package ca.bradj.questown.town;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AwayDurationTest {

    @Test
    void underOneDay_readsHalfADay_notZeroDays() {
        assertEquals("half a day", AwayDuration.describe(12000));
        assertEquals("half a day", AwayDuration.describe(23999));
    }

    @Test
    void oneDay() {
        assertEquals("1 day", AwayDuration.describe(24000));
        assertEquals("1 day", AwayDuration.describe(47999));
    }

    @Test
    void severalDays() {
        assertEquals("3 days", AwayDuration.describe(72000));
    }
}
