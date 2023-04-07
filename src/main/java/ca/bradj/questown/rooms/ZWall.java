package ca.bradj.questown.rooms;

import ca.bradj.questown.logic.RoomDetector;

import java.util.Optional;

public class ZWall {
    public final DoorPos northCorner;
    public final DoorPos southCorner;

    public ZWall(
            DoorPos northCorner,
            DoorPos southCorner
    ) {
        this.northCorner = northCorner;
        this.southCorner = southCorner;
    }
    public static Optional<ZWall> northFromCorner(
            RoomDetector.WallDetector wd,
            DoorPos cornerPos,
            int maxDistFromCorner
    ) {
        int southCornerZ = 0, northCornerZ = 0;
        for (int i = 0; i < maxDistFromCorner; i++) {
            DoorPos op = cornerPos.offset(0, 0, -i);
            if (wd.IsWall(op)) {
                southCornerZ = Math.max(southCornerZ, op.z);
                northCornerZ = Math.min(northCornerZ, op.z);
            }
        }
        if (Math.abs(northCornerZ - southCornerZ) < 3) {
            return Optional.empty();
        }
        return Optional.of(
                new ZWall(cornerPos.WithZ(southCornerZ), cornerPos.WithZ(northCornerZ))
        );
    }

    public static Optional<ZWall> southFromCorner(
            RoomDetector.WallDetector wd,
            DoorPos doorPos,
            int maxDistFromCorner
    ) {
        int southCornerZ = 0, northCornerZ = 0;
        for (int i = 0; i < maxDistFromCorner; i++) {
            DoorPos op = doorPos.offset(0, 0, i);
            if (wd.IsWall(op)) {
                southCornerZ = Math.min(southCornerZ, op.z);
                northCornerZ = Math.max(northCornerZ, op.z);
            }
        }
        if (Math.abs(northCornerZ - southCornerZ) < 2) {
            return Optional.empty();
        }
        return Optional.of(
                new ZWall(doorPos.WithZ(southCornerZ), doorPos.WithZ(northCornerZ))
        );
    }
}
