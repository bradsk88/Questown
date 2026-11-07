package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link MorningRewards}: whether a morning reward is queued. */
class MorningRewardsTest {

    @Test
    void isAnyPending_isFalse_whenQueueEmpty() {
        assertFalse(MorningRewards.isAnyPending(ImmutableList.of()));
    }

    @Test
    void isAnyPending_isTrue_whenQueueHoldsAnyReward() {
        assertTrue(MorningRewards.isAnyPending(ImmutableList.of(new Object())));
    }
}
