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

    public static Optional<XWall> findNorthOrSouthWall(
            int maxDistFromDoor,
            RoomDetector.WallDetector wd,
            XWall wall
    ) {
        int northLength = 0;
        int southLength = 0;
        Optional<ZWall> northWestWall = ZWall.northFromCorner(wd, wall.westCorner, maxDistFromDoor);
        Optional<ZWall> northEastWall = ZWall.northFromCorner(wd, wall.eastCorner, maxDistFromDoor);
        if (northEastWall.isPresent() && northWestWall.isPresent()) {
            northLength = Math.min(
                    northWestWall.get().getLength(),
                    northEastWall.get().getLength()
            );
        }
        Optional<ZWall> southWestWall = ZWall.southFromCorner(wd, wall.westCorner, maxDistFromDoor);
        Optional<ZWall> southEastWall = ZWall.southFromCorner(wd, wall.eastCorner, maxDistFromDoor);
        if (southEastWall.isPresent() && southWestWall.isPresent()) {
            southLength = Math.min(
                    southWestWall.get().getLength(),
                    southEastWall.get().getLength()
            );
        }
        if (northLength != 0 && Math.abs(northLength) > Math.abs(southLength)) {
            if (northLength > 0) {
                XWall foundWall = new XWall(northWestWall.get().northCorner, northEastWall.get().northCorner);
                if (XWallLogic.isConnected(foundWall, wd)) {
                    return Optional.of(foundWall);
                }
                ZWall swWall = northWestWall.get();
                ZWall seWall = northEastWall.get();
                while (swWall.getLength() > 2) {
                    swWall = swWall.shortenNorthEnd(1);
                    seWall = seWall.shortenNorthEnd(1);
                    XWall sWall = new XWall(swWall.northCorner, seWall.northCorner);
                    if (XWallLogic.isConnected(sWall, wd)) {
                        return Optional.of(sWall);
                    }
                }
            }
            return Optional.of(
                    new XWall(northWestWall.get().northCorner, northEastWall.get().northCorner)
            );
        }
        if (southLength != 0) {
            if (southLength > 0) {
                XWall foundWall = new XWall(southWestWall.get().southCorner, southEastWall.get().southCorner);
                if (XWallLogic.isConnected(foundWall, wd)) {
                    return Optional.of(foundWall);
                }
                ZWall swWall = southWestWall.get();
                ZWall seWall = southEastWall.get();
                while (swWall.getLength() > 2) {
                    swWall = swWall.shortenSouthEnd(1);
                    seWall = seWall.shortenSouthEnd(1);
                    XWall sWall = new XWall(swWall.southCorner, seWall.southCorner);
                    if (XWallLogic.isConnected(sWall, wd)) {
                        return Optional.of(sWall);
                    }
                }
            }
            return Optional.of(
                    new XWall(southWestWall.get().southCorner, southEastWall.get().southCorner)
            );
        }
        return Optional.empty();
    }
}
