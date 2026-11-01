package ca.bradj.questown.items;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Wand-on-campfire behaviour is covered by the chicken-arc autotest scenarios.
 */
class WandLightCampfireTest {

    @Test
    @Disabled("Covered by chicken-arc scenario F1_stick_to_campfire (wand click lights the unlit campfire at CAMPFIRE_OFFSET).")
    void unlitCampfireWithinFlagRadius_lights() {
    }

    @Test
    @Disabled("F2 (sleep handoff) is deferred out of scope for headless verification — fake player cannot sleep. See the plan's Scope Boundaries.")
    void litCampfireFallsThroughToSleepPath() {
    }

    @Test
    @Disabled("Covered by chicken-arc scenario wand_on_unlit_campfire_outside_flag.")
    void campfireOutsideAnyFlag_isInert() {
    }
}
