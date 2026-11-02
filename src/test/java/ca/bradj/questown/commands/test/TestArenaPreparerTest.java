package ca.bradj.questown.commands.test;

import ca.bradj.questown.town.HelperChickenRotationDetector;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link TestArenaPreparer}'s options and the retry-counter plumbing.
 * {@link TestArenaPreparer#destroyNearbyFlags} and {@link TestArenaPreparer#flatten} need a
 * real {@code ServerLevel}; the {@code _requiresServerLevel} tests mark that gap.
 */
class TestArenaPreparerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void jobsTrackDefaults_matchPreviousTestExecutorBehavior() {
        TestArenaPreparer.PreparerOptions opts = TestArenaPreparer.PreparerOptions.jobsTrackDefaults();
        Assertions.assertEquals(7, opts.halfWidth(),
                "jobs track's original area was a 15x15 square = halfWidth 7");
        Assertions.assertFalse(opts.killHelperChickens(),
                "jobs track must not sweep chickens (no chickens in jobs scenarios)");
        Assertions.assertFalse(opts.clearFakePlayerInventory(),
                "jobs track must not touch the fake player's inventory");
        Assertions.assertFalse(opts.clearRotationDetectorRetryCounters(),
                "jobs track must not poke chicken-arc singletons");
    }

    @Test
    void preparerOptions_allowsCustomHalfWidth() {
        TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(
                25, true, true, true
        );
        Assertions.assertEquals(25, opts.halfWidth());
        Assertions.assertTrue(opts.killHelperChickens());
        Assertions.assertTrue(opts.clearFakePlayerInventory());
        Assertions.assertTrue(opts.clearRotationDetectorRetryCounters());
    }

    @Test
    void preparerOptions_recordEqualityWorks() {
        TestArenaPreparer.PreparerOptions a = new TestArenaPreparer.PreparerOptions(10, true, false, true);
        TestArenaPreparer.PreparerOptions b = new TestArenaPreparer.PreparerOptions(10, true, false, true);
        Assertions.assertEquals(a, b);
    }

    @Test
    void clearRetryCounters_isReachableFromTestHarness() {
        // Exercise the new public clear method; verifies the hook exists and does
        // not throw when invoked on an empty map.
        Assertions.assertDoesNotThrow(
                HelperChickenRotationDetector::clearRetryCounters
        );
    }

    /**
     * Covers the {@code halfWidth}-configurability contract at the observable
     * layer: the PreparerOptions record round-trips different widths. True
     * behavioral coverage — that {@link TestArenaPreparer#flatten} actually
     * destroys blocks in the larger area — requires a live ServerLevel and is
     * verified only by the in-game autotest harness.
     */
    @Test
    void halfWidthOverride_roundTripsThroughPreparerOptions() {
        for (int width : new int[]{5, 7, 10, 15, 25, 50}) {
            TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(
                    width, false, false, false
            );
            Assertions.assertEquals(width, opts.halfWidth(),
                    "half-width must round-trip for " + width);
        }
    }

    /**
     * Kill-sweep behavior requires a ServerLevel to stand up HelperChickenEntity instances.
     */
    @Test
    @Disabled("Testability gap: requires a real ServerLevel to spawn HelperChickenEntity "
            + "instances and read them back via level.getEntitiesOfClass. Covered end-to-end "
            + "by the in-game autotest run (AutoTestRunner). Per CLAUDE.md we do not simulate.")
    void killHelperChickens_requiresServerLevel() {
        Assertions.fail("see @Disabled reason");
    }

    /**
     * Fake-player inventory clear requires a live ServerPlayer constructed via
     * FakePlayerFactory, which in turn needs a ServerLevel. Same testability
     * gap as above — the contract is verified end-to-end in the in-game run.
     */
    @Test
    @Disabled("Testability gap: needs a FakePlayer which requires a live ServerLevel. "
            + "Verified end-to-end by the in-game autotest run.")
    void clearFakePlayerInventory_requiresServerLevel() {
        Assertions.fail("see @Disabled reason");
    }
}
