package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure planner for the {@code WAITING_FOR_ROOM_BUILD} beat: given the position
 * of a registered door, produce the ordered list of blocks the helper chicken
 * lays to enclose a minimal room around it.
 *
 * <p>Everything here is a pure function of the door position, the level's
 * occupancy, and the set of already-laid blocks — no side effects — so the
 * whole layout is unit-testable without a live world.
 *
 * <p>The layout is a closed rectangle around the door, reusing the same
 * enclosure that RoomRecipes uses to <em>detect</em> a room. A door registered
 * on open ground is a door but not a room (the wall walk has no walls to close
 * around), so the goal must lay a shell that the wall walk accepts. Rather than
 * hand-roll the perimeter — which previously produced a plus shape that the
 * walk could not close — the plan builds an {@link InclusiveSpace} with the
 * door on one edge and lays {@link InclusiveSpaces#getWallPositions} (the full
 * closed perimeter, corners included). That cannot produce a plus, by
 * construction: the perimeter is the one RoomRecipes's own
 * {@code WallWalkingRoomDetection} would walk.
 *
 * <p>The rectangle is {@value #ROOM_SIZE} wide by {@value #ROOM_SIZE} deep.
 * The door sits on the "along" edge; the room extends {@value #INTERIOR_DEPTH}
 * cells inward, plus the door's own edge, for {@value #ROOM_SIZE} cells along
 * the inward axis. The wall walk needs a crawl at least 3 wide and 3 tall, so a
 * 3×3 is the minimum that counts. A wall reads at the scan level when it is
 * solid at BOTH feet and head (see {@code WallDetection.IsWall}), so each
 * perimeter cell is laid at both; there is no ceiling and no floor — room
 * detection is a 2D wall walk and the walkability check needs air at feet and
 * head, which the natural ground and open sky already give.
 */
public final class ChickenRoomShellPlan {

    /** The door's edge to the outer edge of the room, in cells. */
    static final int ROOM_SIZE = 3;
    /** Interior cells back from the door's edge (ROOM_SIZE minus the door's edge). */
    static final int INTERIOR_DEPTH = ROOM_SIZE - 1;

    /** The position a shell block will be laid at, and the scan level its wall column must read at. */
    public record BlockStep(BlockPos pos, int scanY) {
    }

    /** The finished layout: the blocks to lay, and the cell the chicken drops the chest on. */
    public record Plan(List<BlockStep> steps, BlockPos chestCell) {
    }

    private ChickenRoomShellPlan() {
    }

    /**
     * How the doorway sits on its axis. The door block faces outward; its two
     * side gaps (the open cells flanking the door along the doorway row) are
     * found by probing, since a door placed on flat ground has no wall on
     * either side to infer the axis from.
     */
    public record DoorFrame(
            BlockPos doorPos,
            /** The horizontal direction from the door into the room-to-be. */
            Direction inward,
            /** The horizontal direction along the doorway row (perpendicular). */
            Direction along,
            /** The door's lower-half Y — the level the room scan runs at. */
            int baseY
    ) {
    }

    /**
     * Resolve the door's frame. {@code inward} is the direction the door faces
     * into the room (the block the door opens onto); for a free-standing door
     * either perpendicular is a valid "inside," so the one with more open space
     * wins and north/east breaks a tie. {@code along} is the row direction.
     */
    public static DoorFrame frameFor(
            BlockPos doorPos,
            int baseY,
            java.util.function.Predicate<BlockPos> isOccupied
    ) {
        Direction.Axis axis = axisOf(doorPos, baseY, isOccupied);
        // The row runs along `axis`; the room extends into the perpendicular axis.
        // `along` is the row direction; `inward` is the perpendicular. They must be
        // perpendicular or the shell collapses to a plus (both in one axis).
        Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        // pickInward takes the row axis and returns the perpendicular inward direction.
        Direction inward = pickInward(doorPos, baseY, axis, isOccupied);
        return new DoorFrame(doorPos, inward, along, baseY);
    }

    /**
     * The axis the doorway row runs along (the direction of the cells flanking
     * the door). A door on a real wall has solid columns on both sides along
     * one axis, so the row runs along the axis joining those columns. A door on
     * flat ground has none, so default to the row running along X (door faces
     * north/south), which keeps the shell deterministic in the onboarding arena.
     */
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
            return Direction.Axis.X; // columns east/west of the door → the row runs along X
        }
        if (zSides && !xSides) {
            return Direction.Axis.Z;
        }
        // Flat ground (or both): default to the doorway row running along X.
        return Direction.Axis.X;
    }

    /**
     * Pick which perpendicular direction the room extends into: the side with
     * more open cells (so the shell is not built into a hillside), ties broken
     * deterministically.
     */
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

    /** How many of the next {@code INTERIOR_DEPTH} cells in {@code dir} are open. */
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

    /**
     * The {@link InclusiveSpace} rectangle around the door: {@value #ROOM_SIZE}
     * cells along the doorway row, {@value #ROOM_SIZE} cells deep. The door sits
     * in the middle of the "along" edge; the room extends {@value #INTERIOR_DEPTH}
     * cells inward from it. RoomRecipes's {@code WallWalkingRoomDetection} would
     * walk exactly this rectangle from the door, so laying its perimeter is what
     * makes the wall walk accept a room here.
     *
     * <p>The door is centred on the along edge (along offsets -1..+1, door at 0),
     * not at a corner: a door at a corner is a plus, not a closed box, because the
     * wall walk starts at the door and turns, so the door must have wall on both
     * sides along the edge.
     */
    public static InclusiveSpace space(DoorFrame frame) {
        BlockPos door = frame.doorPos();
        Direction along = frame.along();
        Direction in = frame.inward();
        // The along edge is ROOM_SIZE cells wide centred on the door: from along -1
        // to along +1, with the door at along 0. The inward edge runs from the
        // door's edge (inward 0) to inward (ROOM_SIZE - 1).
        BlockPos a = door.relative(along, -(ROOM_SIZE - 1) / 2).relative(in, 0);
        BlockPos b = door.relative(along, (ROOM_SIZE - 1) / 2).relative(in, ROOM_SIZE - 1);
        return InclusiveSpace.from(a.getX(), a.getZ()).to(b.getX(), b.getZ());
    }

    /**
     * The full build order for the shell around {@code frame}: the closed
     * perimeter of {@link #space(DoorFrame)} at feet and head, with the interior
     * left open. Cells already occupied in the world (or already laid, in
     * {@code laid}) are skipped, so the goal can re-plan after every block and
     * after a player edit. The door cell is on the perimeter but is never
     * occupied by this plan — it is the door — so it reads as a wall the wall
     * walk walks around, and the room is enclosed through it.
     */
    public static Plan plan(
            DoorFrame frame,
            java.util.function.Predicate<BlockPos> isOccupied,
            java.util.Set<BlockPos> laid
    ) {
        InclusiveSpace rectangle = space(frame);
        int y = frame.baseY();
        List<BlockStep> steps = new ArrayList<>();

        // The closed perimeter, corners included: what RoomRecipes's wall walk
        // would close around. Laying the whole perimeter is what the manual
        // corner/edge loop could not — it produced a plus with open sides.
        for (Position wall : InclusiveSpaces.getWallPositions(rectangle, true)) {
            BlockPos feet = new BlockPos(wall.x, y, wall.z);
            addPair(steps, feet, y, isOccupied, laid);
        }

        // The chest cell: the first interior cell, at feet level. The interior
        // is everything the perimeter is not; the goal drops the chest on the
        // cell one row in from the door's edge.
        BlockPos chest = new BlockPos(
                frame.doorPos().getX(),
                y,
                frame.doorPos().getZ()
        ).relative(frame.inward(), 1);
        return new Plan(steps, chest);
    }

    /**
     * Add a feet+head wall pair for one cell, skipping occupied/laid cells. A
     * wall only counts at the scan level when it is solid at BOTH feet and
     * head (see {@code WallDetection.IsWall}), so each cell contributes both.
     */
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
