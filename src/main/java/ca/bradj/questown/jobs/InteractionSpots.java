package ca.bradj.questown.jobs;

import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.rooms.XWall;
import ca.bradj.roomrecipes.rooms.ZWall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where a villager stands to use a block inside a room (a job block or a container).
 * Shared by {@code DeclarativeJob} (job blocks) and {@code TownContainers} (chests): the
 * chest path used to step one block toward the door without checking the cell, so a chest
 * whose door-side neighbour is a wall got a walk target inside the wall and the villager
 * ended up standing on the chest (2026-10-17 playtest).
 */
public final class InteractionSpots {

    private InteractionSpots() {
    }

    /**
     * The door-side neighbour if standable, else the first standable horizontal neighbour,
     * else - for a 1x1 room (plus walls) - the door cell; null when none qualifies.
     */
    public static @Nullable BlockPos find(
            BlockPos bp,
            Room room,
            Predicate<BlockPos> isStandable
    ) {
        Direction d = doorDirectionFromCenter(room);
        if (isStandable.test(bp.relative(d))) {
            return bp.relative(d);
        }
        for (Direction dd : Direction.Plane.HORIZONTAL) {
            if (isStandable.test(bp.relative(dd))) {
                return bp.relative(dd);
            }
        }
        if (InclusiveSpaces.calculateArea(room.getSpaces()) == 9) {
            return Positions.ToBlock(room.getDoorPos(), bp.getY());
        }
        return null;
    }

    public static Direction doorDirectionFromCenter(Room room) {
        Optional<XWall> backXWall = room.getBackXWall();
        if (backXWall.isPresent() && backXWall.get().getZ() > room.doorPos.z) {
            return Direction.NORTH;
        }
        if (backXWall.isPresent()) {
            return Direction.SOUTH;
        }

        Optional<ZWall> backZWall = room.getBackZWall();
        if (backZWall.isPresent() && backZWall.get().getX() > room.doorPos.x) {
            return Direction.WEST;
        }
        if (backZWall.isPresent()) {
            return Direction.EAST;
        }
        return Direction.NORTH;
    }
}
