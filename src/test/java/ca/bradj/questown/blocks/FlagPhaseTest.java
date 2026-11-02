package ca.bradj.questown.blocks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Only an active flag runs the town. */
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
        // Saved by name, so renaming would break saved flags.
        Assertions.assertEquals("active", FlagPhase.ACTIVE.getSerializedName());
        Assertions.assertEquals("shutting_down", FlagPhase.SHUTTING_DOWN.getSerializedName());
        Assertions.assertEquals("dormant", FlagPhase.DORMANT.getSerializedName());
    }
}
