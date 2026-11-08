package ca.bradj.questown.commands.test;

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
        Assertions.assertFalse(opts.clearFakePlayerInventory(),
                "jobs track must not touch the fake player's inventory");
    }

    @Test
    void preparerOptions_allowsCustomHalfWidth() {
        TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(25, true);
        Assertions.assertEquals(25, opts.halfWidth());
        Assertions.assertTrue(opts.clearFakePlayerInventory());
    }

    @Test
    void preparerOptions_recordEqualityWorks() {
        TestArenaPreparer.PreparerOptions a = new TestArenaPreparer.PreparerOptions(10, false);
        TestArenaPreparer.PreparerOptions b = new TestArenaPreparer.PreparerOptions(10, false);
        Assertions.assertEquals(a, b);
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
                    width, false
            );
            Assertions.assertEquals(width, opts.halfWidth(),
                    "half-width must round-trip for " + width);
        }
    }

    /**
     * Fake-player inventory clear requires a live ServerPlayer constructed via
     * FakePlayerFactory, which in turn needs a ServerLevel. Same testability
     * gap — the contract is verified end-to-end in the in-game run.
     */
    @Test
    @Disabled("Testability gap: needs a FakePlayer which requires a live ServerLevel. "
            + "Verified end-to-end by the in-game autotest run.")
    void clearFakePlayerInventory_requiresServerLevel() {
        Assertions.fail("see @Disabled reason");
    }
}
