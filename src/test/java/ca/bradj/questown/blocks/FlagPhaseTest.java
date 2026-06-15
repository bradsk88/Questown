package ca.bradj.questown.blocks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Phase 0 verify: a dormant (or shutting-down) flag must not drive the town tick.
 *
 * <p>{@code TownFlagTicker.flagTicksTown(state)} gates the whole town tick on
 * {@code state.getValue(PHASE).ticksTown()}, so this drives the real predicate the ticker
 * consults rather than simulating the tick. The full BlockState→ticker skip is exercised
 * end-to-end by the {@code flag/town_shutdown} autotest (Phase 1).
 */
class FlagPhaseTest {

    @Test
    void activeFlagTicksTown() {
        Assertions.assertTrue(FlagPhase.ACTIVE.ticksTown());
    }

    @Test
    void shuttingDownFlagIsSkippedByTicker() {
        Assertions.assertFalse(FlagPhase.SHUTTING_DOWN.ticksTown());
    }

    @Test
    void dormantFlagIsSkippedByTicker() {
        Assertions.assertFalse(FlagPhase.DORMANT.ticksTown());
    }

    @Test
    void serializedNamesAreStableBlockstateKeys() {
        // Blockstate property values persist by serialized name; renaming these silently
        // orphans saved flags, so pin them.
        Assertions.assertEquals("active", FlagPhase.ACTIVE.getSerializedName());
        Assertions.assertEquals("shutting_down", FlagPhase.SHUTTING_DOWN.getSerializedName());
        Assertions.assertEquals("dormant", FlagPhase.DORMANT.getSerializedName());
    }
}
