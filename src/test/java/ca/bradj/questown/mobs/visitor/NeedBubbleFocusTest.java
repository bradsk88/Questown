package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.town.rooms.AttentionBubbles;
import net.minecraft.core.BlockPos;
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

    // A dead-door winner (a BlockPos) leaves the townie holder null. The hint path runs the same
    // for doors as for townies, so unmetNeedIngredient must not dereference the null townie:
    // that is the crash that booted the client when looking squarely at a dead door.
    @Test
    void Test_UnmetNeedIngredientReturnsNullForNullTownie() {
        Assertions.assertNull(NeedBubbleFocus.unmetNeedIngredient(null));
    }

    // The attention holders (flag with incomplete quests / board with no work requests) ride the
    // same flicker rule as the other holder types. A Target is a distinct type from the door's
    // raw BlockPos holder, and its identity includes the Kind.
    private static final AttentionBubbles.Target FLAG_TARGET =
            new AttentionBubbles.Target(new BlockPos(0, 64, 0), AttentionBubbles.Kind.INCOMPLETE_QUESTS);
    private static final AttentionBubbles.Target BOARD_TARGET =
            new AttentionBubbles.Target(new BlockPos(5, 64, 5), AttentionBubbles.Kind.NO_WORK_REQUESTS);

    @Test
    void Test_AttentionTargetIdentityIncludesKind() {
        // The flag's "!" and a board's "!" at the same position are different candidates.
        AttentionBubbles.Target samePosDifferentKind =
                new AttentionBubbles.Target(FLAG_TARGET.pos(), AttentionBubbles.Kind.NO_WORK_REQUESTS);
        Assertions.assertNotEquals(FLAG_TARGET, samePosDifferentKind);
    }

    @Test
    void Test_AttentionHolderTakesBubbleFromNobody() {
        Assertions.assertFalse(NeedBubbleFocus.keepsBubble(null, 0, FLAG_TARGET, 0.91));
    }

    @Test
    void Test_AttentionHolderKeepsBubbleAgainstMarginallyBetterChallenger() {
        Assertions.assertTrue(NeedBubbleFocus.keepsBubble(FLAG_TARGET, 0.95, BOARD_TARGET, 0.96));
    }

    @Test
    void Test_AttentionKindHintKeys() {
        Assertions.assertEquals("message.questown.attention.incomplete_quests",
                AttentionBubbles.Kind.INCOMPLETE_QUESTS.hintKey);
        Assertions.assertEquals("message.questown.attention.no_work_requests",
                AttentionBubbles.Kind.NO_WORK_REQUESTS.hintKey);
    }
}
