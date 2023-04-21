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
        Optional<XWall> doorWall = WallDetection.findEastToWestWall(maxDistFromDoor, wd, doorPos);
        if (doorWall.isEmpty()) {
            this.corners = ImmutableSet.of();
            return false;
        }
        Optional<XWall> ewWall = WallDetection.findParallelRoomXWall(maxDistFromDoor, wd, doorWall.get());
        if (ewWall.isEmpty()) {
            this.corners = ImmutableSet.of();
            return false;
        }
        if (ewWall.get().westCorner.z != ewWall.get().eastCorner.z) {
            return false;
        }

        if (XWallLogic.isConnected(ewWall.get(), wd)) {
            this.corners = ImmutableSet.of(
                    doorWall.get().westCorner,
                    doorWall.get().eastCorner,
                    ewWall.get().westCorner,
                    ewWall.get().eastCorner
            );
            return true;
        }
        return false;
    }

    public boolean findEastOrWestWallFromDoor(WallDetector wd) {
        Optional<ZWall> doorWall = WallDetection.findNorthToSouthWall(maxDistFromDoor, wd, doorPos);
        if (doorWall.isEmpty()) {
            this.corners = ImmutableSet.of();
            return false;
        }
        Optional<ZWall> ewWall = WallDetection.findParallelRoomZWall(maxDistFromDoor, wd, doorWall.get());
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

}
