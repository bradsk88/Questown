package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class StrandedRescueTest {

    // r20 playtest: flag at 147 71 44, gate spot near 143 71 30.
    private static final List<BlockPos> TOWN = List.of(
            new BlockPos(147, 71, 44),
            new BlockPos(143, 71, 30)
    );

    @Test
    void aVillagerInsideTheTownIsNotOutside() {
        Assertions.assertFalse(StrandedRescue.isOutsideTown(new BlockPos(145, 71, 37), TOWN));
    }

    @Test
    void aVillagerJustPastTheEdgeIsStillInside() {
        Assertions.assertFalse(StrandedRescue.isOutsideTown(new BlockPos(147 + StrandedRescue.MARGIN_XZ, 71, 44), TOWN));
    }

    @Test
    void aVillagerFarFromTheTownIsOutside() {
        Assertions.assertTrue(StrandedRescue.isOutsideTown(new BlockPos(147 + StrandedRescue.MARGIN_XZ + 1, 71, 44), TOWN));
    }

    @Test
    void aVillagerThatFellBelowTheTownIsOutside_evenRightBesideIt() {
        // The r20 case: floating at 154 63 40, 7 blocks from the flag but 8 below the town.
        Assertions.assertTrue(StrandedRescue.isOutsideTown(new BlockPos(154, 63, 40), TOWN));
    }

    @Test
    void aFewBlocksBelowTheFloorIsStillInside() {
        Assertions.assertFalse(StrandedRescue.isOutsideTown(new BlockPos(147, 71 - StrandedRescue.MARGIN_BELOW, 44), TOWN));
    }

    @Test
    void aTownWithNoAnchorsNeverCallsAnyoneOutside() {
        Assertions.assertFalse(StrandedRescue.isOutsideTown(new BlockPos(0, 0, 0), List.of()));
    }

    @Test
    void waterStall_firesAfterTheWindowWithNoProgress() {
        StrandedRescue rescue = new StrandedRescue();
        for (int i = 1; i < StrandedRescue.WATER_STALL_TICKS; i++) {
            Assertions.assertFalse(rescue.observeWater(true, 20.0), "tick " + i);
        }
        Assertions.assertTrue(rescue.observeWater(true, 20.0));
    }

    @Test
    void waterStall_gettingCloserRestartsTheWindow() {
        StrandedRescue rescue = new StrandedRescue();
        for (int i = 1; i < StrandedRescue.WATER_STALL_TICKS; i++) {
            rescue.observeWater(true, 20.0);
        }
        Assertions.assertFalse(rescue.observeWater(true, 18.0));
        Assertions.assertFalse(rescue.observeWater(true, 18.0));
    }

    @Test
    void waterStall_driftingWithoutGettingCloserStillFires() {
        StrandedRescue rescue = new StrandedRescue();
        boolean fired = false;
        for (int i = 0; i < StrandedRescue.WATER_STALL_TICKS; i++) {
            fired = rescue.observeWater(true, 20.0 + (i % 2) * 0.5);
        }
        Assertions.assertTrue(fired);
    }

    @Test
    void waterStall_leavingTheWaterResets() {
        StrandedRescue rescue = new StrandedRescue();
        for (int i = 1; i < StrandedRescue.WATER_STALL_TICKS; i++) {
            rescue.observeWater(true, 20.0);
        }
        Assertions.assertFalse(rescue.observeWater(false, 20.0));
        Assertions.assertFalse(rescue.observeWater(true, 20.0));
    }
}
