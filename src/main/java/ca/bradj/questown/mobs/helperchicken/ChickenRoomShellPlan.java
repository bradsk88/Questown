package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Plans the room the helper chicken builds around a registered door: a {@value #ROOM_SIZE} by {@value #ROOM_SIZE} ring of two-high walls, with no roof or floor. */
public final class ChickenRoomShellPlan {

    /** 4x4 with walls leaves a 2x2 inside, room for the chicken and the player at once. */
    static final int ROOM_SIZE = 4;
    static final int INTERIOR_DEPTH = ROOM_SIZE - 1;

    public record BlockStep(BlockPos pos, int scanY) {
    }

    /** The blocks to lay, and the cell one row in from the door. */
    public record Plan(List<BlockStep> steps, BlockPos chestCell) {
    }

    private ChickenRoomShellPlan() {
    }

    /** Which way the doorway faces. */
    public record DoorFrame(
            BlockPos doorPos,
            Direction inward,
            Direction along,
            /** The door's lower half; the room is scanned at this level. */
            int baseY
    ) {
    }

    /** The door's frame. For a free-standing door the side with more open space is inside. */
    public static DoorFrame frameFor(
            BlockPos doorPos,
            int baseY,
            java.util.function.Predicate<BlockPos> isOccupied
    ) {
        Direction.Axis axis = axisOf(doorPos, baseY, isOccupied);
        Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction inward = pickInward(doorPos, baseY, axis, isOccupied);
        return new DoorFrame(doorPos, inward, along, baseY);
    }

    /** The axis of the doorway row: the one with walls on both sides of the door, or X on flat ground. */
    private static Direction.Axis axisOf(
            BlockPos doorPos,
            int baseY,
            java.util.function.Predicate<BlockPos> isOccupied
    ) {
        boolean xSides = isWallish(doorPos.east(), baseY, isOccupied)
                && isWallish(doorPos.west(), baseY, isOccupied);
        boolean zSides = isWallish(doorPos.north(), baseY, isOccupied)
                && isWallish(doorPos.south(), baseY, isOccupied);
        if (xSides && !zSides) {
            return Direction.Axis.X;
        }
        if (zSides && !xSides) {
            return Direction.Axis.Z;
        }
        return Direction.Axis.X;
    }

    /** The side the room goes on: the one with more open cells, so it isn't built into a hill. */
    private static Direction pickInward(
            BlockPos doorPos,
            int baseY,
            Direction.Axis rowAxis,
            java.util.function.Predicate<BlockPos> isOccupied
    ) {
        Direction a = rowAxis == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
        Direction b = a.getOpposite();
        int openA = openDepth(doorPos, baseY, a, isOccupied);
        int openB = openDepth(doorPos, baseY, b, isOccupied);
        return openA >= openB ? a : b;
    }

    private static int openDepth(
            BlockPos doorPos,
            int baseY,
            Direction dir,
            java.util.function.Predicate<BlockPos> isOccupied
    ) {
        int open = 0;
        for (int i = 1; i <= INTERIOR_DEPTH; i++) {
            BlockPos p = doorPos.relative(dir, i);
            if (!isOccupied.test(new BlockPos(p.getX(), baseY, p.getZ()))
                    && !isOccupied.test(new BlockPos(p.getX(), baseY + 1, p.getZ()))) {
                open++;
            }
        }
        return open;
    }

    private static boolean isWallish(
            BlockPos pos,
            int baseY,
            java.util.function.Predicate<BlockPos> isOccupied
    ) {
        BlockPos p = new BlockPos(pos.getX(), baseY, pos.getZ());
        return isOccupied.test(p) && isOccupied.test(p.above());
    }

    /** The rectangle around the door, with the door in the middle of one edge, as room detection walks it. */
    public static InclusiveSpace space(DoorFrame frame) {
        BlockPos door = frame.doorPos();
        Direction along = frame.along();
        Direction in = frame.inward();
        // Split the cells either side of the door so the rectangle is square for any size.
        int floorHalf = (ROOM_SIZE - 1) / 2;
        int ceilHalf = (ROOM_SIZE - 1) - floorHalf;
        BlockPos a = door.relative(along, -floorHalf).relative(in, 0);
        BlockPos b = door.relative(along, ceilHalf).relative(in, ROOM_SIZE - 1);
        return InclusiveSpace.from(a.getX(), a.getZ()).to(b.getX(), b.getZ());
    }

    /** Whether the room can be built here, checked before laying anything: the door cell and every wall cell must be open at feet and head. */
    public static Outcome feasibility(
            BlockPos doorPos,
            int baseY,
            java.util.function.Predicate<BlockPos> isOccupied
    ) {
        DoorFrame frame = frameFor(doorPos, baseY, isOccupied);
        if (isOccupied.test(doorPos) || isOccupied.test(doorPos.above())) {
            return new Outcome(false, "the door cell is not open (a block is already there) at "
                    + doorPos.toShortString());
        }
        InclusiveSpace rectangle = space(frame);
        int y = frame.baseY();
        // Only the wall cells; the inside stays open.
        for (Position wall : InclusiveSpaces.getWallPositions(rectangle, true)) {
            BlockPos p = new BlockPos(wall.x, y, wall.z);
            if (isOccupied.test(p) || isOccupied.test(p.above())) {
                return new Outcome(
                        false,
                        "the room footprint is blocked by a wall at " + p.toShortString()
                );
            }
        }
        return new Outcome(true, "");
    }

    public record Outcome(boolean clear, String reason) {
    }

    /** The walls still to lay, feet and head, skipping cells already filled, so the goal can re-plan after every block. */
    public static Plan plan(
            DoorFrame frame,
            java.util.function.Predicate<BlockPos> isOccupied,
            java.util.Set<BlockPos> laid
    ) {
        InclusiveSpace rectangle = space(frame);
        int y = frame.baseY();
        List<BlockStep> steps = new ArrayList<>();

        for (Position wall : InclusiveSpaces.getWallPositions(rectangle, true)) {
            BlockPos feet = new BlockPos(wall.x, y, wall.z);
            addPair(steps, feet, y, isOccupied, laid);
        }

        BlockPos chest = new BlockPos(
                frame.doorPos().getX(),
                y,
                frame.doorPos().getZ()
        ).relative(frame.inward(), 1);
        return new Plan(steps, chest);
    }

    /** A feet and head pair for one cell, skipping filled cells. */
    private static void addPair(
            List<BlockStep> steps,
            BlockPos feet,
            int scanY,
            java.util.function.Predicate<BlockPos> isOccupied,
            java.util.Set<BlockPos> laid
    ) {
        for (BlockPos p : new BlockPos[]{ feet, feet.above() }) {
            if (!isOccupied.test(p) && !laid.contains(p)) {
                steps.add(new BlockStep(p, scanY));
            }
        }
    }
}
