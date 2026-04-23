package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * F1/F2/F3/F4 curriculum coverage now lives in the chicken-arc autotest suite —
 * see {@code docs/conventions/agent-chicken-verification-loop.md}. The pure
 * transition logic stays in {@link ChickenArcTransitionsTest}.
 */
class CurriculumSequenceTest {

    @Test
    @Disabled("Covered by chicken-arc scenario F1_stick_to_campfire.")
    void f1_stickToCampfireLit() {
    }

    @Test
    @Disabled("Covered by chicken-arc scenario F4_seeds_to_statue.")
    void f4_seedsDeliveryRunsTransform() {
    }

    @Test
    @Disabled("Covered by chicken-arc scenario F3_build_room_to_welcome_mat (chest-before-sign is in the scripted action list).")
    void f3_outOfOrderPlacementRespectsR7b() {
    }

    @Test
    @Disabled("Covered by chicken-arc scenario F4_seeds_to_statue via MarkVillagerUiOpened + MarkFlagUiOpened actions.")
    void uiObservations_fireFromRealOpenPaths() {
    }
}
