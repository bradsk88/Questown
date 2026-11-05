package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Where the storeroom chest goes. */
class HelperChickenSunsetChestGoalTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void isReplaceable_air_isTrue() {
        Assertions.assertTrue(call(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void isReplaceable_snowLayer_isTrue_regressionGuardForSnowyBiomes() {
        // Snow is replaceable.
        Assertions.assertTrue(call(Blocks.SNOW.defaultBlockState()));
    }

    @Test
    void isReplaceable_tallGrassAndShortGrass_areTrue() {
        Assertions.assertTrue(call(Blocks.GRASS.defaultBlockState()));
        Assertions.assertTrue(call(Blocks.TALL_GRASS.defaultBlockState()));
    }

    @Test
    void isReplaceable_fluids_areTrue() {
        Assertions.assertTrue(call(Blocks.WATER.defaultBlockState()));
        Assertions.assertTrue(call(Blocks.LAVA.defaultBlockState()));
    }

    @Test
    void isReplaceable_solids_areFalse() {
        Assertions.assertFalse(call(Blocks.COBBLESTONE.defaultBlockState()));
        Assertions.assertFalse(call(Blocks.GRASS_BLOCK.defaultBlockState()));
        Assertions.assertFalse(call(Blocks.OAK_LOG.defaultBlockState()));
    }

    private static boolean call(BlockState state) {
        return HelperChickenSunsetChestGoal.isReplaceable(state);
    }

    @Test
    void pickChestPos_allClear_picksAtMaxDistance() {
        BlockPos player = new BlockPos(0, 64, 0);
        BlockPos chicken = new BlockPos(2, 64, 2);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, chicken, 4, anywhere -> true
        );
        Assertions.assertTrue(pick.isPresent());
        // The search starts farthest out.
        int dx = pick.get().getX() - player.getX();
        int dz = pick.get().getZ() - player.getZ();
        Assertions.assertEquals(4, Math.max(Math.abs(dx), Math.abs(dz)),
                "expected distance-4 pick when everywhere is passable; got " + pick.get());
    }

    @Test
    void pickChestPos_distance4Blocked_fallsBackToCloserCell() {
        BlockPos player = new BlockPos(0, 64, 0);
        Set<BlockPos> blocked = new HashSet<>();
        addRing(blocked, player, 4);
        Predicate<BlockPos> passable = p -> !blocked.contains(p);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, new BlockPos(0, 64, 0), 4, passable
        );
        Assertions.assertTrue(pick.isPresent());
        int dist = Math.max(
                Math.abs(pick.get().getX() - player.getX()),
                Math.abs(pick.get().getZ() - player.getZ())
        );
        Assertions.assertTrue(dist >= 1 && dist <= 3,
                "distance-4 ring blocked → expected fallback to distance 1..3, got " + dist);
    }

    @Test
    void pickChestPos_allRingsBlocked_fallsBackToChickenPos() {
        // With every cell blocked, the chicken's own cell is the last resort.
        BlockPos player = new BlockPos(0, 64, 0);
        BlockPos chicken = new BlockPos(10, 64, 10);
        Predicate<BlockPos> passable = p -> p.equals(chicken);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, chicken, 4, passable
        );
        Assertions.assertEquals(Optional.of(chicken), pick);
    }

    @Test
    void pickChestPos_everythingBlocked_returnsEmpty() {
        BlockPos player = new BlockPos(0, 64, 0);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, new BlockPos(10, 64, 10), 4, p -> false
        );
        Assertions.assertTrue(pick.isEmpty());
    }

    @Test
    void pickChestPos_picksAtChickenGroundY_notPlayerY_regressionForSunsetStall() {
        // A player standing higher doesn't raise the chest; it stays at the chicken's height.
        BlockPos player = new BlockPos(0, 65, 0);
        BlockPos chicken = new BlockPos(0, 64, -2);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, chicken, 4, anywhere -> true
        );
        Assertions.assertTrue(pick.isPresent());
        Assertions.assertEquals(64, pick.get().getY(),
                "chest target must be on the chicken's ground Y (64), not the player's Y (65); got " + pick.get());
    }

    @Test
    void pickChestPos_snowAtFar_solidsAtNear_picksSnowCell() {
        // Only the snow cell is passable, and the picker takes it.
        BlockPos player = new BlockPos(0, 64, 0);
        BlockPos snowCell = player.north(4);
        Predicate<BlockPos> passable = p -> p.equals(snowCell);
        Optional<BlockPos> pick = HelperChickenSunsetChestGoal.pickChestPos(
                player, new BlockPos(99, 64, 99), 4, passable
        );
        Assertions.assertEquals(Optional.of(snowCell), pick);
    }

    @Test
    void hasArrived_withinTightRadius_isTrueRegardlessOfNav() {
        Assertions.assertTrue(HelperChickenSunsetChestGoal.hasArrived(1.5D, false));
        Assertions.assertTrue(HelperChickenSunsetChestGoal.hasArrived(1.5D, true));
    }

    @Test
    void hasArrived_pathfinderSettledShort_isTrue_regressionForSunsetStall() {
        // The path stops about 1.5 blocks short; once it is done, that counts as arrived.
        Assertions.assertTrue(HelperChickenSunsetChestGoal.hasArrived(2.4D, true),
                "navigation settled at 2.4 sqr must count as arrival");
    }

    @Test
    void hasArrived_outsideTightRadiusButStillMoving_isFalse() {
        Assertions.assertFalse(HelperChickenSunsetChestGoal.hasArrived(2.4D, false));
    }

    @Test
    void hasArrived_navDoneButFarAway_isFalse() {
        // A failed path far away is not an arrival.
        Assertions.assertFalse(HelperChickenSunsetChestGoal.hasArrived(25.0D, true));
    }

    private static void addRing(Set<BlockPos> sink, BlockPos center, int distance) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                sink.add(center.offset(dx * distance, 0, dz * distance));
            }
        }
    }

    @Test
    void chestCandidates_firstIsTheInteriorCellFarthestFromTheDoor_forEveryFacing() {
        // The chest goes in the far corner whichever way the door faces.
        for (Direction inward : Direction.Plane.HORIZONTAL) {
            BlockPos door = new BlockPos(0, 64, 0);
            ChickenRoomShellPlan.DoorFrame frame = new ChickenRoomShellPlan.DoorFrame(
                    door, inward, inward.getClockWise(), 64);
            InclusiveSpace space = ChickenRoomShellPlan.space(frame);
            Position doorPos = new Position(door.getX(), door.getZ());

            List<Position> candidates = HelperChickenSunsetChestGoal.chestCandidates(
                    List.of(space), doorPos);

            Assertions.assertEquals(4, candidates.size(), "2x2 interior, inward=" + inward);
            Position first = candidates.get(0);
            for (Position other : candidates) {
                Assertions.assertTrue(
                        distSq(first, doorPos) >= distSq(other, doorPos),
                        "inward=" + inward + ": first candidate " + first
                                + " is closer to the door than " + other
                );
            }
            Assertions.assertTrue(distSq(first, doorPos) > 2,
                    "inward=" + inward + ": first candidate " + first + " touches the doorway");
        }
    }

    private static int distSq(Position a, Position b) {
        int dx = a.x - b.x;
        int dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
