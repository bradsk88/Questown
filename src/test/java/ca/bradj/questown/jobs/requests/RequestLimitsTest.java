package ca.bradj.questown.jobs.requests;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class RequestLimitsTest {

    @Test
    void unsetRequests_useTheDefault() {
        Assertions.assertEquals(BoardRequests.PAUSE_AT, new RequestLimits<String>().get("food"));
    }

    @Test
    void aSetLimit_isKept_andClamped() {
        RequestLimits<String> l = new RequestLimits<>();
        Assertions.assertEquals(12, l.set("food", 12));
        Assertions.assertEquals(12, l.get("food"));
        Assertions.assertEquals(RequestLimits.MIN, l.set("food", 0));
        Assertions.assertEquals(RequestLimits.MAX, l.set("food", 1000));
    }

    /** Quick + clicks all count: the board sends a step, not a value. */
    @Test
    void steps_accumulate_andClamp() {
        RequestLimits<String> limits = new RequestLimits<>();
        limits.adjust("bread", 1);
        limits.adjust("bread", 1);
        limits.adjust("bread", 1);
        Assertions.assertEquals(BoardRequests.PAUSE_AT + 3, limits.get("bread"));
        for (int i = 0; i < 100; i++) {
            limits.adjust("bread", -1);
        }
        Assertions.assertEquals(RequestLimits.MIN, limits.get("bread"));
    }

    @Test
    void stocked_pausesAtEachRequestsOwnLimit() {
        Map<String, Integer> stock = Map.of("food", 10, "coal", 10);
        RequestLimits<String> l = new RequestLimits<>();
        l.set("food", 16);
        Assertions.assertEquals(ImmutableSet.of("coal"),
                BoardRequests.stocked(List.of("food", "coal"), stock::get, r -> false, l::get));
    }
}
