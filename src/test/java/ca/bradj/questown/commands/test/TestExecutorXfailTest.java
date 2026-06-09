package ca.bradj.questown.commands.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit coverage for the {@code expectedFailure} (xfail) flag (U3).
 *
 * <p>The inversion inside {@link TestExecutor#getWarpPassed()} (warp-fails ⇒ suite-pass) cannot be
 * unit-tested without a live {@link net.minecraft.server.level.ServerLevel}. The xfail flag is
 * general harness machinery (the organizer/fetch warp gap that first motivated it is now closed by
 * {@code RelocateRequestedItemWarpRule}). Here we pin the two pure, decoupled pieces: the blueprint
 * flag round-trips, and the reporting formatter emits the right XFAIL/XPASS/PASS/FAIL label for each
 * (passed, expectedFailure) pair.
 */
class TestExecutorXfailTest {

    private static TestBlueprint minimalBlueprint() {
        return new TestBlueprint(
                TestBlueprint.RoomType.INDOOR,
                List.of(),
                List.of(),
                net.minecraft.core.BlockPos.ZERO,
                net.minecraft.core.BlockPos.ZERO,
                new net.minecraft.resources.ResourceLocation("questown", "x"),
                new TestExpectation(List.of(), 0, 0)
        );
    }

    @Test
    void expectedFailure_defaultsFalse_andRoundTrips() {
        TestBlueprint base = minimalBlueprint();
        Assertions.assertFalse(base.expectedFailure());
        Assertions.assertTrue(base.withExpectedFailure(true).expectedFailure());
        // withExpectedFailure preserves the rest of the blueprint
        Assertions.assertEquals(base.roomType(), base.withExpectedFailure(true).roomType());
    }

    @Test
    void normalScenario_passLabel() {
        String line = AutotestLogFormatter.formatScenario("jobs", "x", true, false);
        Assertions.assertTrue(line.contains("[PASS]"), line);
    }

    @Test
    void normalScenario_failLabel() {
        String line = AutotestLogFormatter.formatScenario("jobs", "x", false, false);
        Assertions.assertTrue(line.contains("[FAIL]"), line);
    }

    @Test
    void expectedFailure_warpFails_isXfail() {
        // getWarpPassed() returns true (suite pass) when an expected-failure warp fails.
        String line = AutotestLogFormatter.formatScenario("jobs", "x", true, true);
        Assertions.assertTrue(line.contains("[XFAIL]"), line);
        Assertions.assertFalse(line.contains("[XPASS]"), line);
    }

    @Test
    void expectedFailure_warpPasses_isXpassAlarm() {
        // getWarpPassed() returns false (suite fail) when an expected-failure warp unexpectedly passes.
        String line = AutotestLogFormatter.formatScenario("jobs", "x", false, true);
        Assertions.assertTrue(line.contains("[XPASS]"), line);
        Assertions.assertTrue(line.contains("remove expectedFailure"), line);
    }
}
