package ca.bradj.questown.jobs.declarative;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestedLootPreferenceTest {

    private static final int CAPACITY = 6;

    private static List<String> prefer(List<String> rolled, List<String> second) {
        return RequestedLootPreference.prefer(rolled, second, "leather"::equals, CAPACITY, n -> 0);
    }

    @Test
    void aRequestedItemReplacesANonRequestedOne() {
        List<String> full = List.of("beef", "wool", "beef", "feather", "mutton", "beef");

        assertEquals(List.of("leather", "wool", "beef", "feather", "mutton", "beef"),
                prefer(full, List.of("leather")));
    }

    @Test
    void aRequestedItemFillsAnEmptySlotFirst() {
        assertEquals(List.of("beef", "wool", "leather"), prefer(List.of("beef", "wool"), List.of("leather")));
    }

    @Test
    void nonRequestedItemsFromTheSecondRollAreDropped() {
        assertEquals(List.of("beef", "wool"), prefer(List.of("beef", "wool"), List.of("beef", "string")));
    }

    @Test
    void onceEverySlotIsRequestedTheRestAreDropped() {
        List<String> leather = List.of("leather", "leather", "leather", "leather", "leather", "leather");

        assertEquals(leather, prefer(leather, List.of("leather", "leather")));
    }

    @Test
    void theReplacedSlotIsPickedAmongNonRequestedOnly() {
        List<String> full = List.of("leather", "beef", "leather", "wool", "leather", "leather");

        List<String> out = RequestedLootPreference.prefer(full, List.of("leather"), "leather"::equals, CAPACITY, n -> n - 1);

        assertEquals(List.of("leather", "beef", "leather", "leather", "leather", "leather"), out);
    }
}
