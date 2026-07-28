package ca.bradj.questown.mobs.visitor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * The flicker rule for need bubbles (ADR-0011): exactly one townie shows a bubble, and clustered
 * townies must not trade it back and forth as the crosshair drifts.
 *
 * <p>Only the decision is covered. Which townies are eligible, and how well each aligns with the
 * crosshair, needs a client level and a player — {@link NeedBubbleFocus#tick} is not reachable from
 * a unit test, so the cone threshold itself is verified in-game rather than here.
 */
class NeedBubbleFocusTest {

    private static final UUID HOLDER = UUID.randomUUID();
    private static final UUID CHALLENGER = UUID.randomUUID();

    @Test
    void Test_NobodyHoldsBubbleWhenNobodyQualifies() {
        Assertions.assertTrue(NeedBubbleFocus.keepsBubble(null, 0, null, 0));
    }

    @Test
    void Test_FirstQualifyingTownieTakesBubbleImmediately() {
        Assertions.assertFalse(NeedBubbleFocus.keepsBubble(null, 0, CHALLENGER, 0.91));
    }

    @Test
    void Test_HolderKeepsBubbleWhileStillTheBestChoice() {
        Assertions.assertTrue(NeedBubbleFocus.keepsBubble(HOLDER, 0.95, HOLDER, 0.93));
    }

    @Test
    void Test_HolderKeepsBubbleAgainstMarginallyBetterChallenger() {
        // Challenger is better, but not by enough — this is the case that would otherwise flicker.
        Assertions.assertTrue(NeedBubbleFocus.keepsBubble(HOLDER, 0.95, CHALLENGER, 0.96));
    }

    @Test
    void Test_ClearlyBetterChallengerTakesBubble() {
        Assertions.assertFalse(NeedBubbleFocus.keepsBubble(HOLDER, 0.90, CHALLENGER, 0.99));
    }

    @Test
    void Test_BubbleIsDroppedWhenNobodyQualifiesAnymore() {
        Assertions.assertFalse(NeedBubbleFocus.keepsBubble(HOLDER, 0.95, null, 0));
    }
}
