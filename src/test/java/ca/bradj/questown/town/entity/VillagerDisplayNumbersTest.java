package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Pins the 2026-10-14 playtest ruling on townie display numbers ("Villager N"):
// numbers are monotonic and never re-issued - when a townie dies/leaves its number
// is retired, not handed to the next arrival.
class VillagerDisplayNumbersTest {

    private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID C = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void numbersAreAssignedInArrivalOrder() {
        VillagerDisplayNumbers n = new VillagerDisplayNumbers();
        assertEquals(1, n.assign(A));
        assertEquals(2, n.assign(B));
        assertEquals(3, n.assign(C));
        assertEquals(1, n.numberFor(A));
        assertEquals(0, n.numberFor(UUID.randomUUID()), "unassigned vuid has no number");
    }

    @Test
    void aRetiredNumberIsNeverReissued() {
        VillagerDisplayNumbers n = new VillagerDisplayNumbers();
        n.assign(A);
        n.assign(B);
        n.assign(C);
        n.retire(C); // C leaves; its number 3 is retired
        assertEquals(0, n.numberFor(C));
        UUID d = UUID.randomUUID();
        assertEquals(4, n.assign(d), "the next arrival gets 4, not the retired 3");
    }

    @Test
    void assignIsIdempotentForAKnownVuid() {
        VillagerDisplayNumbers n = new VillagerDisplayNumbers();
        assertEquals(1, n.assign(A));
        assertEquals(1, n.assign(A), "a reload re-registering the same townie keeps its number");
        assertEquals(2, n.assign(B), "the idempotent re-assign did not burn a number");
    }

    @Test
    void restoreKeepsNumbersAndTheMonotonicHighWaterMark() {
        VillagerDisplayNumbers n = new VillagerDisplayNumbers();
        n.restore(Map.of(A, 1, B, 5), 5);
        assertEquals(1, n.numberFor(A));
        assertEquals(5, n.numberFor(B));
        assertEquals(6, n.assign(C), "continues past the restored high-water mark");

        // a corrupted/hand-edited NBT with a value above the stored counter:
        // the counter catches up, so no stored number is ever shadowed by a re-issue
        VillagerDisplayNumbers n2 = new VillagerDisplayNumbers();
        n2.restore(Map.of(A, 7), 3);
        assertEquals(8, n2.assign(B));
    }
}
