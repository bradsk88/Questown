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

/** {@link ChickenRoomShellPlan}: which cells the chicken lays, so a room with a gap, a door without walls, or an interior nobody can stand in fails here. North is -z, east +x. */
class ChickenRoomShellPlanTest {

    private static final Predicate<BlockPos> EMPTY_WORLD = p -> false;
    private static final Set<BlockPos> NO_LAI;

    static {
        NO_LAI = new HashSet<>();
    }

    /** A doorway row along east, with north inward. */
    private static ChickenRoomShellPlan.DoorFrame frame() {
        return new ChickenRoomShellPlan.DoorFrame(
                new BlockPos(0, 64, 0), Direction.NORTH, Direction.EAST, 64);
    }

    @Test
    void frame_flatDoor_defaultsRowToX_andPicksOpenInward() {
        // On flat ground the row runs along X; a tie on open space picks north.
        ChickenRoomShellPlan.DoorFrame f =
                ChickenRoomShellPlan.frameFor(new BlockPos(0, 64, 0), 64, EMPTY_WORLD);
        Assertions.assertEquals(Direction.NORTH, f.inward());
        Assertions.assertEquals(64, f.baseY());
    }

    @Test
    void frame_occupiedInSouth_picksNorthAsInward() {
        // A hill to the south: build north.
        Predicate<BlockPos> occupiedSouth = p -> p.getZ() > 0;
        ChickenRoomShellPlan.DoorFrame f =
                ChickenRoomShellPlan.frameFor(new BlockPos(0, 64, 0), 64, occupiedSouth);
        Assertions.assertEquals(Direction.NORTH, f.inward());
    }

    @Test
    void feasibility_emptyFootprint_isClear() {
        ChickenRoomShellPlan.Outcome o =
                ChickenRoomShellPlan.feasibility(new BlockPos(0, 64, 0), 64, EMPTY_WORLD);
        Assertions.assertTrue(o.clear(), "a clear footprint must pass the pre-flight");
    }

    @Test
    void feasibility_occupiedDoorCell_isNotClear() {
        // A block in the door cell: back off.
        Set<BlockPos> doorCell = new HashSet<>();
        doorCell.add(new BlockPos(0, 64, 0));
        ChickenRoomShellPlan.Outcome o =
                ChickenRoomShellPlan.feasibility(new BlockPos(0, 64, 0), 64,
                        p -> doorCell.contains(p));
        Assertions.assertFalse(o.clear(),
                "a block in the door cell must fail the pre-flight");
        Assertions.assertTrue(o.reason().contains("door cell"),
                "the reason must name the door cell: " + o.reason());
    }

    @Test
    void feasibility_wallInFootprint_isNotClear() {
        // A wall on the room's ring: back off.
        ChickenRoomShellPlan.DoorFrame f =
                ChickenRoomShellPlan.frameFor(new BlockPos(0, 64, 0), 64, EMPTY_WORLD);
        InclusiveSpace rect = ChickenRoomShellPlan.space(f);
        java.util.Collection<Position> walls =
                InclusiveSpaces.getWallPositions(rect, true);
        Assertions.assertFalse(walls.isEmpty(), "the footprint has a perimeter");
        Position wall = walls.iterator().next();
        BlockPos perimeterCell = new BlockPos(wall.x, 64, wall.z);
        Set<BlockPos> intruding = new HashSet<>();
        intruding.add(perimeterCell);
        ChickenRoomShellPlan.Outcome o =
                ChickenRoomShellPlan.feasibility(new BlockPos(0, 64, 0), 64,
                        p -> intruding.contains(p));
        Assertions.assertFalse(o.clear(),
                "a wall on a cell the pre-flight scans must fail it");
        Assertions.assertTrue(o.reason().contains("footprint"),
                "the reason must name the footprint: " + o.reason());
    }

    @Test
    void feasibility_groundBelowFootprint_isAllowed() {
        // Ground under the walls is fine.
        Set<BlockPos> ground = new HashSet<>();
        ground.add(new BlockPos(0, 63, 0));
        ground.add(new BlockPos(0, 63, -1));
        ChickenRoomShellPlan.Outcome o =
                ChickenRoomShellPlan.feasibility(new BlockPos(0, 64, 0), 64,
                        p -> ground.contains(p));
        Assertions.assertTrue(o.clear(),
                "ground below the footprint must not fail the pre-flight");
    }

    @Test
    void plan_laysAWholeClosedBox_notAPlus() {
        // The ring is closed.
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        InclusiveSpace rectangle = ChickenRoomShellPlan.space(frame());
        Predicate<Position> laid = p -> laidAtFeet(plan, p);
        Assertions.assertTrue(
                InclusiveSpaces.isWhole(rectangle, laid, true),
                "the laid cells must be a whole closed box around the door, not a plus"
        );
    }

    @Test
    void plan_laysTheDoorEdgeSoTheDoorReadsAsAWall() {
        // The door's edge has wall on both sides of the door.
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        InclusiveSpace rectangle = ChickenRoomShellPlan.space(frame());
        Assertions.assertTrue(
                InclusiveSpaces.isWhole(rectangle, p -> laidAtFeet(plan, p), true),
                "edges and corners must all be laid so the wall walk cannot escape"
        );
    }

    @Test
    void plan_leavesTheInteriorUnlaid() {
        // The inside stays open.
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
    void plan_boxIsSquareFourByFour() {
        // ROOM_SIZE by ROOM_SIZE, with the door walled on both sides.
        InclusiveSpace rectangle = ChickenRoomShellPlan.space(frame());
        int width = rectangle.getEastX() - rectangle.getWestX() + 1;
        int height = rectangle.getSouthZ() - rectangle.getNorthZ() + 1;
        Assertions.assertEquals(4, width, "box must be 4 wide along the row");
        Assertions.assertEquals(4, height, "box must be 4 deep inward");
        Assertions.assertTrue(
                InclusiveSpaces.isWhole(rectangle, p -> laidAtFeet(
                        ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI), p), true),
                "a 4×4 shell must close whole, not read as a plus"
        );
    }

    @Test
    void plan_interiorFitsChickenAndPlayer() {
        // At least two inside cells, for the chicken and the player.
        InclusiveSpace rectangle = ChickenRoomShellPlan.space(frame());
        long interiorCells = InclusiveSpaces.getPositions(
                rectangle, InclusiveSpaces.PositionType.INTERIOR_ONLY).size();
        Assertions.assertTrue(
                interiorCells >= 2,
                "the interior must fit both the chicken and the player; got "
                        + interiorCells + " interior cell(s)"
        );
    }

    @Test
    void plan_chestCellIsOneRowInFromTheDoor() {
        ChickenRoomShellPlan.Plan plan =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
        Assertions.assertEquals(new BlockPos(0, 64, -1), plan.chestCell());
    }

    @Test
    void plan_skipsAlreadyOccupiedAndLaidCells() {
        // A filled cell isn't laid again.
        ChickenRoomShellPlan.Plan plan0 =
                ChickenRoomShellPlan.plan(frame(), EMPTY_WORLD, NO_LAI);
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
        // With west inward, the box extends west.
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
        // Every block is at feet or head height, no higher.
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

    /** Whether the plan lays a block at {@code p}'s x and z, at feet level. */
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
