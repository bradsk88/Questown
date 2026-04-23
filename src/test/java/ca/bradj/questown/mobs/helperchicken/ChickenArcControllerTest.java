package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for {@link ChickenArcController} touches the flag BE,
 * ServerLevel, and a spawned chicken entity — none of which can be constructed
 * in a bare JUnit harness. The transition logic is unit-tested in
 * {@link ChickenArcTransitionsTest} and the bubble mapping in
 * {@link ChickenArcBubblesTest}; this test class exists so the gap is visible
 * per CLAUDE.md.
 */
class ChickenArcControllerTest {

    @Test
    void TODO_controllerTick_driveFullArcWithRealFlagBe() {
        Assertions.fail(
                "TODO[U4]: ChickenArcController.tick(flag) touches TownFlagBlockEntity, " +
                        "ServerLevel, and a spawned HelperChickenEntity — none constructible " +
                        "in a bare unit test. The pure pieces (ChickenArcTransitions, " +
                        "ChickenArcBubbles) are covered separately. F1 and F4 end-to-end " +
                        "coverage happens in /_qtdev per the plan."
        );
    }

    @Test
    void TODO_observeClearsEphemeralFlagsAfterTransition() {
        Assertions.fail(
                "TODO[U4]: After SUNSET_AND_MAP advances to WAITING_FOR_WALL_BLOCK, the " +
                        "transient chickenObservedSleepSinceSunset flag should read false so a " +
                        "later-day sleep doesn't double-advance a future beat. Not unit-testable " +
                        "without a real flag BE. Verified in /_qtdev."
        );
    }
}
