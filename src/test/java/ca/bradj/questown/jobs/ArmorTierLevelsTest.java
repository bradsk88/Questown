package ca.bradj.questown.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArmorTierLevelsTest {

    @Test
    void armorTiersAreGatedLikeToolTiers() {
        assertEquals(3, ArmorTierLevels.forPrefix("leather"));
        assertEquals(8, ArmorTierLevels.forPrefix("iron"));
        assertEquals(11, ArmorTierLevels.forPrefix("golden"));
        assertEquals(14, ArmorTierLevels.forPrefix("diamond"));
    }

    @Test
    void anUnknownMaterialKeepsTheDefault() {
        assertEquals(Work.DEFAULT_AVAILABLE_AT_LEVEL, ArmorTierLevels.forPrefix("chainmail"));
    }
}
