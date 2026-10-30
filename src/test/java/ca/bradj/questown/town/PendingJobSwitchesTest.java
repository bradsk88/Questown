package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PendingJobSwitchesTest {

    @Test
    void aRequestedSwitch_isTakenOnce() {
        PendingJobSwitches<String, String> p = new PendingJobSwitches<>();
        p.request("hunter", "sword_full_day");
        Assertions.assertEquals("sword_full_day", p.take("hunter"));
        Assertions.assertNull(p.take("hunter"));
    }

    @Test
    void theLatestRequestWins() {
        PendingJobSwitches<String, String> p = new PendingJobSwitches<>();
        p.request("hunter", "sword_half_day");
        p.request("hunter", "sword_full_day");
        Assertions.assertEquals("sword_full_day", p.take("hunter"));
    }

    @Test
    void otherVillagers_haveNothingPending() {
        PendingJobSwitches<String, String> p = new PendingJobSwitches<>();
        p.request("hunter", "sword_full_day");
        Assertions.assertNull(p.take("cook"));
    }
}
