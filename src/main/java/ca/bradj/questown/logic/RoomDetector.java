package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import ca.bradj.questown.rooms.XWall;
import ca.bradj.questown.rooms.ZWall;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;

public class RoomDetector {
    private final int maxDistFromDoor;
    private final Position doorPos;
    private ImmutableSet<Position> corners = ImmutableSet.of();

    public boolean isRoom() {
        return corners.size() == 4;
    }

    public ImmutableSet<Position> getCorners() {
        return corners;
    }

    public Position getDoorPos() {
        return doorPos;
    }

    public interface WallDetector {
        boolean IsWall(Position dp);
    }

    public RoomDetector(
            Position dp,
            int maxDistanceFromDoor
    ) {
        this.doorPos = dp;
        this.maxDistFromDoor = maxDistanceFromDoor;
    }

    public void update(WallDetector wd) {
        if (this.checkEastWestFromDoor(wd)) {
            return;
        }
        this.checkNorthSouthFromDoor(wd);
    }

    public boolean checkEastWestFromDoor(WallDetector wd) {
        Optional<XWall> wall = findEastToWestWall(wd, doorPos);
        if (wall.isEmpty()) {
            this.corners = ImmutableSet.of();
            return false;
        }
        Optional<XWall> nsWall = findNorthOrSouthWall(wd, wall.get());
        if (nsWall.isEmpty()) {
            return false;
        }
        if (nsWall.get().eastCorner.z != nsWall.get().westCorner.z) {
            return false;
        }

        XWall doorWall = wall.get();
        if (isConnected(nsWall.get(), wd)) {
            this.corners = ImmutableSet.of(
                    wall.get().westCorner,
                    wall.get().eastCorner,
                    nsWall.get().eastCorner,
                    nsWall.get().westCorner
            );
            return true;
        }

        boolean connected = false;
        if (doorWall.eastCorner.z > wall.get().eastCorner.z) {
            for (int i = doorWall.eastCorner.z - 1; i > wall.get().eastCorner.z; i--) {
                XWall shifted = new XWall(doorWall.westCorner.WithZ(i), doorWall.eastCorner.WithZ(i));
                if (isConnected(shifted, wd)) {
                    connected = true;
                    doorWall = shifted;
                    break;
                }
            }
        } else {
            for (int i = wall.get().eastCorner.z - 1; i > doorWall.eastCorner.z; i--) { // TODO: Unit test for reverse comparison and --
                XWall shifted = new XWall(doorWall.westCorner.WithZ(i), doorWall.eastCorner.WithZ(i));
                if (isConnected(shifted, wd)) {
                    connected = true;
                    doorWall = shifted;
                    break;
                }
            }
        }

        if (!connected) {
            return false;
        }

        this.corners = ImmutableSet.of(
                wall.get().westCorner,
                wall.get().eastCorner,
                doorWall.eastCorner,
                doorWall.westCorner
        );
        return true;
    }

    public boolean checkNorthSouthFromDoor(WallDetector wd) {
        Optional<ZWall> doorWall = WallDetection.findNorthToSouthWall(maxDistFromDoor, wd, doorPos);
        if (doorWall.isEmpty()) {
            this.corners = ImmutableSet.of();
            return false;
        }
        Optional<ZWall> ewWall = WallDetection.findEastOrWestWall(maxDistFromDoor, wd, doorWall.get());
        if (ewWall.isEmpty()) {
            this.corners = ImmutableSet.of();
            return false;
        }
        if (ewWall.get().northCorner.x != ewWall.get().southCorner.x) {
            return false;
        }

        if (ZWallLogic.isConnected(ewWall.get(), wd)) {
            this.corners = ImmutableSet.of(
                    doorWall.get().northCorner,
                    doorWall.get().southCorner,
                    ewWall.get().northCorner,
                    ewWall.get().southCorner
            );
            return true;
        }
        return false;

//        return false;
//        boolean connected = false;
//        if (xWall.eastCorner.z > doorWall.get().eastCorner.z) {
//            for (int i = xWall.eastCorner.z - 1; i > doorWall.get().eastCorner.z; i--) {
//                XWall shifted = new XWall(xWall.westCorner.WithZ(i), xWall.eastCorner.WithZ(i));
//                if (isConnected(shifted, wd)) {
//                    connected = true;
//                    xWall = shifted;
//                    break;
//                }
//            }
//        } else {
//            for (int i = doorWall.get().eastCorner.z - 1; i > xWall.eastCorner.z; i--) { // TODO: Unit test for reverse comparison and --
//                XWall shifted = new XWall(xWall.westCorner.WithZ(i), xWall.eastCorner.WithZ(i));
//                if (isConnected(shifted, wd)) {
//                    connected = true;
//                    xWall = shifted;
//                    break;
//                }
//            }
//        }
//
//        if (!connected) {
//            return;
//        }
//
//        this.corners = ImmutableSet.of(
//                doorWall.get().westCorner,
//                doorWall.get().eastCorner,
//                xWall.eastCorner,
//                xWall.westCorner
//        );
    }

    private boolean isConnected(
            XWall wall,
            WallDetector wd
    ) {
        int width = wall.eastCorner.x - wall.westCorner.x;
        for (int i = 0; i < width; i++) {
            if (!wd.IsWall(wall.westCorner.offset(i, 0, 0))) {
                return false;
            }
        }
        return true;
    }

    private Optional<XWall> findNorthOrSouthWall(
            WallDetector wd,
            XWall wall
    ) {
        int northLength = 0;
        int southLength = 0;
        Optional<ZWall> northWestWall = ZWall.northFromCorner(wd, wall.westCorner, maxDistFromDoor);
        Optional<ZWall> northEastWall = ZWall.northFromCorner(wd, wall.eastCorner, maxDistFromDoor);
        if (northEastWall.isPresent() && northWestWall.isPresent()) {
            northLength = Math.min(
                    northWestWall.get().northCorner.z - northWestWall.get().southCorner.z,
                    northEastWall.get().northCorner.z - northEastWall.get().southCorner.z
            );
        }
        Optional<ZWall> southWestWall = ZWall.southFromCorner(wd, wall.westCorner, maxDistFromDoor);
        Optional<ZWall> southEastWall = ZWall.southFromCorner(wd, wall.eastCorner, maxDistFromDoor);
        if (southEastWall.isPresent() && southWestWall.isPresent()) {
            southLength = Math.min(
                    southWestWall.get().northCorner.z - southWestWall.get().southCorner.z,
                    southEastWall.get().northCorner.z - southEastWall.get().southCorner.z
            );
        }
        if (northLength != 0 && Math.abs(northLength) > Math.abs(southLength)) {
            if (northLength > 0) { // TODO: Unit test for inverting this logic
                return Optional.of(
                        new XWall(northWestWall.get().southCorner, northEastWall.get().southCorner)
                );
            }
            return Optional.of(
                    new XWall(northWestWall.get().northCorner, northEastWall.get().northCorner)
            );
        }
        if (southLength != 0) {
            if (southLength > 0) { // TODO: Unit test for inverting this logic
                return Optional.of(
                        new XWall(southWestWall.get().northCorner, southEastWall.get().northCorner)
                );
            }
            return Optional.of(
                    new XWall(southWestWall.get().southCorner, southEastWall.get().southCorner)
            );
        }
        return Optional.empty();
    }

    private Optional<XWall> findEastToWestWall(
            WallDetector wd,
            Position doorPos
    ) {
        int westCornerX = Integer.MAX_VALUE, eastCornerX = -Integer.MAX_VALUE;
        boolean started = false;
        for (int i = 0; i < maxDistFromDoor; i++) {
            Position op = doorPos.offset(i, 0, 0);
            if (wd.IsWall(op)) {
                started = true;
                westCornerX = Math.min(westCornerX, op.x);
                eastCornerX = Math.max(eastCornerX, op.x);
            } else if (started) {
                break;
            }
        }
        for (int i = 0; i < maxDistFromDoor; i++) {
            Position op = doorPos.offset(-i, 0, 0);
            if (wd.IsWall(op)) {
                started = true;
                westCornerX = Math.min(westCornerX, op.x);
                eastCornerX = Math.max(eastCornerX, op.x);
            } else if (started) {
                break;
            }
        }
        if (!started) {
            return Optional.empty();
        }
        if (Math.abs(eastCornerX - westCornerX) < 2) {
            return Optional.empty();
        }
        return Optional.of(
                new XWall(doorPos.WithX(westCornerX), doorPos.WithX(eastCornerX))
        );
    }

}
