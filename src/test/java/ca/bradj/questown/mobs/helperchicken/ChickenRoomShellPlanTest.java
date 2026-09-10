package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure-function coverage for {@link ChickenRoomShellPlan}. The plan is the part
 * of the room-build beat a unit test can pin down without a live world: it
 * decides which cells the chicken lays, so a wrong layout (a hole the wall walk
 * escapes through, an interior a villager cannot stand in, a door not walled)
 * shows up here, not only in a held run.
 *
 * <p>Direction convention (vanilla): {@code NORTH} = -z, {@code SOUTH} = +z,
 * {@code EAST} = +x, {@code WEST} = -x. The plan lays the closed perimeter of
 * an {@link InclusiveSpace} around the door, so a layout that is not an enclosed
 * box (the plus the plan used to produce) is caught by {@code isWhole}, not just
 * by the presence of individual cells.
 */
class ChickenRoomShellPlanTest {

    private static final Predicate<BlockPos> EMPTY_WORLD = p -> false;
    private static final Set<BlockPos> NO_LAI;

    static {
        NO_LAI = new HashSet<>();
    }

    /** An EAST doorway row with NORTH inward: along cells step in X, depth in -Z. */
    private static ChickenRoomShellPlan.DoorFrame frame() {
        return new ChickenRoomShellPlan.DoorFrame(
                new BlockPos(0, 64, 0), Direction.NORTH, Direction.EAST, 64);
    }

    // -- frame resolution ----------------------------------------------------

    @Test
    void frame_flatDoor_defaultsRowToX_andPicksOpenInward() {
        // A door on flat ground (no wall on either side): the row runs along X
        // and the inward direction is whichever perpendicular has more open
        // depth. With an empty world both are equal, so the tie-break
        // (north over east) picks north.
        ChickenRoomShellPlan.DoorFrame f =
                ChickenRoomShellPlan.frameFor(new BlockPos(0, 64, 0), 64, EMPTY_WORLD);
        Assertions.assertEquals(Direction.NORTH, f.inward());
        Assertions.assertEquals(64, f.baseY());
    }

    @Test
    void frame_occupiedInSouth_picksNorthAsInward() {
        // A hillside to the south (+z): the shell must be built north (-z),
        // the open side.
        Predicate<BlockPos> occupiedSouth = p -> p.getZ() > 0;
        ChickenRoomShellPlan.DoorFrame f =
                ChickenRoomShellPlan.frameFor(new BlockPos(0, 64, 0), 64, occupiedSouth);
        Assertions.assertEquals(Direction.NORTH, f.inward());
    }

    // -- enclosure: the regression guard the plan used to fail ---------------

    @Test
    void plan_laysAWholeClosedBox_notAPlus() {
        // The plan lays the closed perimeter of an InclusiveSpace. A plus shape
        // (open sides) is not whole: isWhole returns false, so a plus can never
        // reach this assertion passing. This is the test the manual layout was
        // missing.
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        InclusiveSpace rectangle = ChickenRoomShellPlan.space(frame());
        // The cells the plan lays at feet, lifted to the InclusiveSpace's x/z.
        Predicate<Position> laid = p -> laidAtFeet(plan, p);
        Assertions.assertTrue(
                InclusiveSpaces.isWhole(rectangle, laid, true),
                "the laid cells must be a whole closed box around the door, not a plus"
        );
    }

    @Test
    void plan_laysTheDoorEdgeSoTheDoorReadsAsAWall() {
        // The door sits on the "along" edge of the box; the perimeter lays that
        // edge (minus the door cell itself, which is the door). The corner posts
        // on that edge make the door a wall at its scan level.
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        InclusiveSpace rectangle = ChickenRoomShellPlan.space(frame());
        // The door edge runs along the row; a corner post on it is laid.
        Assertions.assertTrue(
                InclusiveSpaces.isWhole(rectangle, p -> laidAtFeet(plan, p), true),
                "edges and corners must all be laid so the wall walk cannot escape"
        );
    }

    @Test
    void plan_leavesTheInteriorUnlaid() {
        // The interior cells (everything that is not the perimeter) must stay
        // open — the walkability check needs air there.
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        InclusiveSpace rectangle = ChickenRoomShellPlan.space(frame());
        for (Position interior : InclusiveSpaces.getPositions(
                rectangle, InclusiveSpaces.PositionType.INTERIOR_ONLY)) {
            Assertions.assertFalse(
                    laidAtFeet(plan, interior),
                    "interior cell " + interior + " must not be laid"
            );
        }
    }

    @Test
    void plan_chestCellIsOneRowInFromTheDoor() {
        // Chest cell = the door's position, one row inward, at feet level.
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        // frame: inward NORTH (-z), so one row in is (0, 64, -1).
        Assertions.assertEquals(new BlockPos(0, 64, -1), plan.chestCell());
    }

    @Test
    void plan_skipsAlreadyOccupiedAndLaidCells() {
        // A cell the player filled (or the chicken already laid) must not be
        // re-laid — the plan skips it instead of looping on it.
        ChickenRoomShellPlan.Plan plan0 =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        // Pick one laid perimeter cell to "already occupy".
        BlockPos occupied = plan0.steps().get(0).pos();
        Set<BlockPos> occupiedSet = new HashSet<>();
        occupiedSet.add(occupied);
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), p -> occupiedSet.contains(p), NO_LAI);
        for (ChickenRoomShellPlan.BlockStep s : plan.steps()) {
            Assertions.assertNotEquals(occupied, s.pos(), "occupied cell must be skipped");
        }
    }

    @Test
    void plan_westInwardDoor_buildsWest() {
        // A frame with inward = WEST: the chest cell is one block west of the
        // door (along 0, depth 1 steps in the inward direction). The box extends
        // west, so it is whole on that side too.
        ChickenRoomShellPlan.DoorFrame f =
                new ChickenRoomShellPlan.DoorFrame(
                        new BlockPos(0, 64, 0), Direction.WEST, Direction.EAST, 64);
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(f, EMPTY_WORLD, NO_LAI);
        Assertions.assertEquals(new BlockPos(-1, 64, 0), plan.chestCell());
        Assertions.assertTrue(
                InclusiveSpaces.isWhole(
                        ChickenRoomShellPlan.space(f), p -> laidAtFeet(plan, p), true),
                "the box must be whole regardless of which side it extends into"
        );
    }

    @Test
    void plan_allStepsAreAtFeetOrHeadLevel() {
        // Every laid block is at the scan level (feet) or one above (head) —
        // the wall read needs a solid at both. No block should sit higher.
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        for (ChickenRoomShellPlan.BlockStep s : plan.steps()) {
            Assertions.assertTrue(
                    s.pos().getY() == 64 || s.pos().getY() == 65,
                    "block at " + s.pos() + " is not at feet or head level"
            );
            Assertions.assertEquals(64, s.scanY(), "scan level is the door's base Y");
        }
    }

    /**
     * Whether the plan lays a block at {@code p} (by its x/z), reading the
     * feet-level cell — the scan's {@code IsWall} needs a solid at feet and head,
     * and the plan lays both, so a laid feet cell implies a laid head cell.
     */
    private static boolean laidAtFeet(
            ChickenRoomShellPlan.Plan plan, Position p
    ) {
        for (ChickenRoomShellPlan.BlockStep s : plan.steps()) {
            if (s.pos().getX() == p.x && s.pos().getZ() == p.z && s.pos().getY() == 64) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlock(
            List<ChickenRoomShellPlan.BlockStep> steps, BlockPos p
    ) {
        for (ChickenRoomShellPlan.BlockStep s : steps) {
            if (s.pos().equals(p)) {
                return true;
            }
        }
        return false;
    }
}
