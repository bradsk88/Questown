package ca.bradj.questown.town.quests;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the morning-reward "pending" rule that backs the "all quests done" morning
 * line. The rule was extracted into {@link MorningRewards} so it could be exercised
 * with plain collections — {@code MCMorningRewards} itself cannot be constructed in
 * a headless JVM (its constructor resolves the Forge {@code RewardsInit} registry,
 * whose static initializer fails outside a bootstrapped mod environment).
 */
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
