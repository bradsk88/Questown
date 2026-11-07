package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.town.rooms.AttentionBubbles;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/** Need bubbles: one villager shows a bubble at a time, and neighbours don't trade it as the crosshair drifts. */
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
        // Better, but not by enough.
        Assertions.assertTrue(NeedBubbleFocus.keepsBubble(HOLDER, 0.95, CHALLENGER, 0.96));
    }

    @Test
    void Test_ClearlyBetterChallengerTakesBubble() {
        Assertions.assertFalse(NeedBubbleFocus.keepsBubble(HOLDER, 0.90, CHALLENGER, 0.99));
    }

    @Test
    void Test_HolderOutOfTheConeLosesToAnyChallenger() {
        // The holder's live score, 0 once it leaves the cone.
        Assertions.assertFalse(NeedBubbleFocus.keepsBubble(HOLDER, 0, CHALLENGER, 0.91));
    }

    @Test
    void Test_BubbleIsDroppedWhenNobodyQualifiesAnymore() {
        Assertions.assertFalse(NeedBubbleFocus.keepsBubble(HOLDER, 0.95, null, 0));
    }

    // A dead-door holder leaves no villager; the hint must not read the missing one.
    @Test
    void Test_UnmetNeedIngredientReturnsNullForNullTownie() {
        Assertions.assertNull(NeedBubbleFocus.unmetNeedIngredient(null));
    }

    // "!" holders follow the same rule; a Target includes its kind.
    private static final AttentionBubbles.Target FLAG_TARGET =
            new AttentionBubbles.Target(new BlockPos(0, 64, 0), AttentionBubbles.Kind.INCOMPLETE_QUESTS);
    private static final AttentionBubbles.Target BOARD_TARGET =
            new AttentionBubbles.Target(new BlockPos(5, 64, 5), AttentionBubbles.Kind.NO_WORK_REQUESTS);

    @Test
    void Test_AttentionTargetIdentityIncludesKind() {
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

    @Test
    void Test_VillagerNeedsShowOnlyInTheWorkingDay() {
        Assertions.assertTrue(NeedBubbleFocus.villagerNeedsShowAt(0));
        Assertions.assertTrue(NeedBubbleFocus.villagerNeedsShowAt(11499));
        Assertions.assertFalse(NeedBubbleFocus.villagerNeedsShowAt(11500));
        Assertions.assertFalse(NeedBubbleFocus.villagerNeedsShowAt(18000));
        Assertions.assertFalse(NeedBubbleFocus.villagerNeedsShowAt(23999));
        Assertions.assertTrue(NeedBubbleFocus.villagerNeedsShowAt(24000 * 3 + 100));
    }

    @Test
    void Test_MissingRoomBubbleCyclesThroughTheRoomsBlocks() {
        java.util.List<String> blocks = java.util.List.of("blacksmiths_table", "door");
        Assertions.assertEquals("blacksmiths_table", NeedBubbleFocus.cycle(blocks, 10));
        Assertions.assertEquals("door", NeedBubbleFocus.cycle(blocks, 11));
        Assertions.assertNull(NeedBubbleFocus.cycle(java.util.List.of(), 11));
    }
}
