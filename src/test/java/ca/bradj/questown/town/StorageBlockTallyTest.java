package ca.bradj.questown.town;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StorageBlockTallyTest {

    @Test
    void oneVillagerForAMinute_isABlocker() {
        StorageBlockTally t = new StorageBlockTally();
        for (int i = 0; i < 59; i++) {
            t.observe(1, false, 20);
        }
        Assertions.assertFalse(t.isBlocker(), "1180 villager-ticks");
        t.observe(1, false, 20);
        Assertions.assertTrue(t.isBlocker(), "1200 villager-ticks");
    }

    @Test
    void blockedVillagersAddUp() {
        StorageBlockTally t = new StorageBlockTally();
        for (int i = 0; i < 20; i++) {
            t.observe(3, false, 20);
        }
        Assertions.assertTrue(t.isBlocker(), "three villagers for 20 s");
    }

    @Test
    void aFreeSlotOrNobodyBlockedResets() {
        StorageBlockTally t = new StorageBlockTally();
        for (int i = 0; i < 60; i++) {
            t.observe(1, false, 20);
        }
        t.observe(1, true, 20);
        Assertions.assertFalse(t.isBlocker());
        for (int i = 0; i < 60; i++) {
            t.observe(1, false, 20);
        }
        t.observe(0, false, 20);
        Assertions.assertFalse(t.isBlocker());
    }
}
