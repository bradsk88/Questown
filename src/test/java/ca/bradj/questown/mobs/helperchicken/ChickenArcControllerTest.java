package ca.bradj.questown.mobs.helperchicken;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * {@link ChickenArcController} integration coverage lives in the headless
 * {@code ChickenArcAllExecutor} suite — see
 * {@code docs/conventions/agent-chicken-verification-loop.md}. The pure pieces
 * are covered by {@link ChickenArcTransitionsTest} and {@link ChickenArcBubblesTest};
 * the behaviors below require a live flag BE + spawned chicken, which the
 * autotest harness provides.
 */
class ChickenArcControllerTest {

    @Test
    @Disabled("Covered by chicken-arc scenarios F1_stick_to_campfire, F3_build_room_to_welcome_mat, F4_seeds_to_statue. Run ./gradlew runServer -Dquestown.autotest=true.")
    void controllerTick_driveFullArcWithRealFlagBe() {
    }

    @Test
    @Disabled("Covered indirectly by F3_build_room_to_welcome_mat — SUNSET_AND_MAP advances before the wall-block beat, and the F3 run asserts the expected final beat without double-advancing.")
    void observeClearsEphemeralFlagsAfterTransition() {
    }
}
