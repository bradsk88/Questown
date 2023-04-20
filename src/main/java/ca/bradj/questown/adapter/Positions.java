package ca.bradj.questown.adapter;

import ca.bradj.questown.core.space.InclusiveSpace;
import ca.bradj.questown.core.space.Position;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Positions {

    public static BlockPos ToBlock(Position p) {
        return new BlockPos(p.x, p.y, p.z);
    }

    public static InclusiveSpace getInclusiveSpace(Collection<Position> doors) {
        if (doors.size() < 2) {
            throw new IllegalArgumentException(
                    "Must provide at least two different positions"
            );
        }

        Set<Position> uniqueDoors = new HashSet<>(doors);
        if (uniqueDoors.size() < 2) {
            throw new IllegalArgumentException(
                    "Must provide at least two different positions"
            );
        }

        Position firstDoor = doors.iterator().next();
        int minX = firstDoor.x;
        int minY = firstDoor.y;
        int minZ = firstDoor.z;
        int maxX = firstDoor.x;
        int maxY = firstDoor.y;
        int maxZ = firstDoor.z;

        for (Position door : doors) {
            if (door.x < minX) {
                minX = door.x;
            } else if (door.x > maxX) {
                maxX = door.x;
            }

            if (door.y < minY) {
                minY = door.y;
            } else if (door.y > maxY) {
                maxY = door.y;
            }

            if (door.z < minZ) {
                minZ = door.z;
            } else if (door.z > maxZ) {
                maxZ = door.z;
            }
        }

        return new InclusiveSpace(new Position(minX, minY, minZ), new Position(maxX, maxY, maxZ));
    }

    public static Position FromBlockPos(BlockPos blockPos) {
        return new Position(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }
}
