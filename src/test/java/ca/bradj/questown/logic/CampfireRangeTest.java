package ca.bradj.questown.logic;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Playtest r12 #2: a lit campfire one block up a slope from the flag never completed the
 * Campfire quest, because the scan and the wand both required the flag's exact Y.
 */
class CampfireRangeTest {

    private static final BlockPos FLAG = new BlockPos(0, 74, 0);
    private static final int RADIUS = 10;

    @Test
    void campfireOneBlockUpTheSlope_isInRange() {
        Assertions.assertTrue(CampfireRange.contains(FLAG, new BlockPos(4, 75, 0), RADIUS));
    }

    @Test
    void campfireTwoBlocksBelow_isInRange() {
        Assertions.assertTrue(CampfireRange.contains(FLAG, new BlockPos(0, 72, 3), RADIUS));
    }

    @Test
    void campfireThreeBlocksUp_isOutOfRange() {
        Assertions.assertFalse(CampfireRange.contains(FLAG, new BlockPos(1, 77, 1), RADIUS));
    }

    @Test
    void campfireAtTheRadius_isOutOfRange() {
        Assertions.assertFalse(CampfireRange.contains(FLAG, new BlockPos(RADIUS, 74, 0), RADIUS));
    }

    @Test
    void scanTriesTheFlagsOwnYFirst() {
        Assertions.assertEquals(0, CampfireRange.Y_OFFSETS[0]);
        Assertions.assertEquals(2 * CampfireRange.VERTICAL_TOLERANCE + 1, CampfireRange.Y_OFFSETS.length);
    }
}
