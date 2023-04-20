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
        if (this.findNorthOrSouthWallFromDoor(wd)) {
            return;
        }
        this.findEastOrWestWallFromDoor(wd);
    }

    public boolean findNorthOrSouthWallFromDoor(WallDetector wd) {
        Optional<XWall> wall = findEastToWestWall(wd, doorPos);
        if (wall.isEmpty()) {
            this.corners = ImmutableSet.of();
            return false;
        }
        Optional<XWall> nsWall = WallDetection.findNorthOrSouthWall(
                maxDistFromDoor, wd, wall.get()
        );
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

    public boolean findEastOrWestWallFromDoor(WallDetector wd) {
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
