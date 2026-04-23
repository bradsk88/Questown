package ca.bradj.questown.items;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Wand-on-campfire behaviour (R14) is covered by chicken-arc autotest scenarios
 * — see {@code docs/conventions/agent-chicken-verification-loop.md}.
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
