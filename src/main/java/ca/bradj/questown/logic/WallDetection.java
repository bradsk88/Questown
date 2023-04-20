package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import ca.bradj.questown.rooms.XWall;
import ca.bradj.questown.rooms.ZWall;

import java.util.Optional;

public class WallDetection {

    public static Optional<ZWall> findNorthToSouthWall(
            int maxDistFromDoor,
            RoomDetector.WallDetector wd,
            Position doorPos
    ) {
        int northCornerZ = Integer.MAX_VALUE, southCornerZ = -Integer.MAX_VALUE;
        boolean started = false;
        for (int i = 0; i < maxDistFromDoor; i++) {
            Position op = doorPos.offset(0, 0, i);
            if (wd.IsWall(op)) {
                started = true;
                northCornerZ = Math.min(northCornerZ, op.z);
                southCornerZ = Math.max(southCornerZ, op.z);
            } else if (started) {
                break;
            }
        }
        for (int i = 0; i < maxDistFromDoor; i++) {
            Position op = doorPos.offset(0, 0, -i);
            if (wd.IsWall(op)) {
                started = true;
                northCornerZ = Math.min(northCornerZ, op.z);
                southCornerZ = Math.max(southCornerZ, op.z);
            } else if (started) {
                break;
            }
        }
        if (!started) {
            return Optional.empty();
        }
        if (Math.abs(southCornerZ - northCornerZ) < 2) {
            return Optional.empty();
        }
        return Optional.of(
                new ZWall(doorPos.WithZ(northCornerZ), doorPos.WithZ(southCornerZ))
        );
    }

    public static Optional<ZWall> findEastOrWestWall(
            int maxDistFromDoor,
            RoomDetector.WallDetector wd,
            ZWall doorWall
    ) {
        int eastLength = 0;
        Optional<XWall> northEastWall = XWallLogic.eastFromCorner(wd, doorWall.northCorner, maxDistFromDoor);
        Optional<XWall> southEastWall = XWallLogic.eastFromCorner(wd, doorWall.southCorner, maxDistFromDoor);
        if (northEastWall.isPresent() && southEastWall.isPresent()) {
            eastLength = Math.min(
                    southEastWall.get().eastCorner.x - southEastWall.get().westCorner.x,
                    northEastWall.get().eastCorner.x - northEastWall.get().westCorner.x
            );
        }
        if (eastLength > 0) {
            return Optional.of(new ZWall(northEastWall.get().eastCorner, southEastWall.get().eastCorner));
        }

        Optional<XWall> northWestWall = XWallLogic.westFromCorner(wd, doorWall.northCorner, maxDistFromDoor);
        Optional<XWall> southWestWall = XWallLogic.westFromCorner(wd, doorWall.southCorner, maxDistFromDoor);

        int westLength = 0;
        if (northWestWall.isPresent() && southWestWall.isPresent()) {
            westLength = Math.min(
                    southWestWall.get().eastCorner.x - southWestWall.get().westCorner.x,
                    northWestWall.get().eastCorner.x - northWestWall.get().westCorner.x
            );
        }
        if (westLength > 0) {
            return Optional.of(new ZWall(northWestWall.get().westCorner, southWestWall.get().westCorner));
        }
        return Optional.empty();
    }
}
