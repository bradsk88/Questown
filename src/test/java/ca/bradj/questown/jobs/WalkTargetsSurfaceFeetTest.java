package ca.bradj.questown.jobs;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class WalkTargetsSurfaceFeetTest {

    private static final BlockPos DOOR_LOWER = new BlockPos(83, 64, 3);
    private static final BlockPos DOOR_UPPER = DOOR_LOWER.above();
    private static final BlockPos WALL_ABOVE_DOOR = DOOR_UPPER.above();

    /** The live cook (dev playtest 2026-10-29): door, door, wall, then the roof. */
    private static final Set<BlockPos> SOLID = Set.of(DOOR_LOWER, DOOR_UPPER, WALL_ABOVE_DOOR);
    private static final Set<BlockPos> DOORS = Set.of(DOOR_LOWER, DOOR_UPPER);

    private static BlockPos lift(BlockPos feet) {
        return WalkTargets.surfaceFeet(feet, SOLID::contains, DOORS::contains);
    }

    @Test
    void doorwaySpot_staysInTheDoorway_evenWhenReapplied() {
        BlockPos feet = DOOR_LOWER;
        for (int i = 0; i < 4; i++) {
            feet = lift(feet);
        }
        Assertions.assertEquals(DOOR_LOWER, feet);
    }

    @Test
    void solidNonDoorSpot_isLiftedOneBlock() {
        BlockPos slab = new BlockPos(10, 64, 10);
        Assertions.assertEquals(slab.above(), WalkTargets.surfaceFeet(slab, Set.of(slab)::contains, p -> false));
    }

    @Test
    void openSpot_isUnchanged() {
        BlockPos air = new BlockPos(10, 64, 10);
        Assertions.assertEquals(air, WalkTargets.surfaceFeet(air, p -> false, p -> false));
    }
}
