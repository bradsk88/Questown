package ca.bradj.questown.jobs.gatherer;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootRollsTest {

    private static List<String> roll(int count, int maxRolls, List<List<String>> rolls) {
        Deque<List<String>> q = new ArrayDeque<>(rolls);
        return LootRolls.roll(count, maxRolls, () -> q.isEmpty() ? List.of() : q.pop(), String::isEmpty);
    }

    @Test
    void everyAttemptBringsBackAnItem_emptyRollsDontCount() {
        List<String> out = roll(3, 100, List.of(List.of("beef"), List.of(""), List.of("wool"), List.of(), List.of("leather")));

        assertEquals(List.of("beef", "wool", "leather"), out);
    }

    @Test
    void itStopsOnceItHasTheCount() {
        assertEquals(List.of("beef", "wool"), roll(2, 100, List.of(List.of("beef"), List.of("wool"), List.of("leather"))));
    }

    @Test
    void itGivesUpAfterMaxRolls() {
        assertEquals(List.of("beef"), roll(3, 2, List.of(List.of("beef"), List.of(""), List.of("wool"))));
    }
}
