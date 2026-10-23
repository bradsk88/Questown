package ca.bradj.questown.town;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The town-sleep rule: the town stays awake while a player is within the tick radius of one of its
 * townies, and measures from the flag only while it has no townies.
 */
class TownSleepRadiusTest {

    private static final BlockPos FLAG = new BlockPos(0, 64, 0);
    private static final long RADIUS_SQR = 10_000; // the TownTickRadius default: 100 blocks

    @Test
    void Test_WithNoTowniesTheFlagIsTheAnchor() {
        long d = TownSleepRadius.nearestPlayerDistSqr(
                List.of(new BlockPos(30, 64, 40)), List.of(), FLAG
        );
        Assertions.assertEquals(2500, d);
    }

    @Test
    void Test_WithTowniesTheNearestTownieIsTheAnchor() {
        long d = TownSleepRadius.nearestPlayerDistSqr(
                List.of(new BlockPos(150, 64, 0)),
                List.of(new BlockPos(5, 64, 0), new BlockPos(140, 64, 0)),
                FLAG
        );
        Assertions.assertEquals(100, d);
    }

    @Test
    void Test_PlayerNearAFarTownieKeepsTheTownAwake() {
        long d = TownSleepRadius.nearestPlayerDistSqr(
                List.of(new BlockPos(220, 64, 0)),
                List.of(new BlockPos(150, 64, 0)),
                FLAG
        );
        Assertions.assertFalse(TownSleepRadius.isAsleep(d, RADIUS_SQR));
    }

    @Test
    void Test_PlayerAtTheFlagWithEveryTownieFarAwaySleepsTheTown() {
        long d = TownSleepRadius.nearestPlayerDistSqr(
                List.of(FLAG),
                List.of(new BlockPos(150, 64, 0)),
                FLAG
        );
        Assertions.assertTrue(TownSleepRadius.isAsleep(d, RADIUS_SQR));
    }

    @Test
    void Test_TheNearestOfSeveralPlayersCounts() {
        long d = TownSleepRadius.nearestPlayerDistSqr(
                List.of(new BlockPos(500, 64, 0), new BlockPos(10, 64, 0)),
                List.of(new BlockPos(0, 64, 0)),
                FLAG
        );
        Assertions.assertEquals(100, d);
    }

    @Test
    void Test_NoPlayersIsInfinitelyFarAndAsleep() {
        long d = TownSleepRadius.nearestPlayerDistSqr(List.of(), List.of(new BlockPos(0, 64, 0)), FLAG);
        Assertions.assertEquals(Long.MAX_VALUE, d);
        Assertions.assertTrue(TownSleepRadius.isAsleep(d, RADIUS_SQR));
    }

    @Test
    void Test_ExactlyOnTheRadiusIsAwake() {
        Assertions.assertFalse(TownSleepRadius.isAsleep(RADIUS_SQR, RADIUS_SQR));
    }
}
