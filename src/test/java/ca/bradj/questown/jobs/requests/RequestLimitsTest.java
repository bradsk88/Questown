package ca.bradj.questown.jobs.requests;

import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** Dev playtest 2026-10-29: see and adjust each request's limit. */
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

    @Test
    void stocked_pausesAtEachRequestsOwnLimit() {
        Map<String, Integer> stock = Map.of("food", 10, "coal", 10);
        RequestLimits<String> l = new RequestLimits<>();
        l.set("food", 16);
        Assertions.assertEquals(ImmutableSet.of("coal"),
                BoardRequests.stocked(List.of("food", "coal"), stock::get, r -> false, l::get));
    }
}
