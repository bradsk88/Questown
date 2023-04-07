package ca.bradj.questown.logic;

import ca.bradj.questown.rooms.DoorPos;
import ca.bradj.questown.rooms.XWall;
import ca.bradj.questown.rooms.ZWall;
import com.google.common.collect.ImmutableSet;

import java.util.Optional;

public class RoomDetector {
    private final int maxDistFromDoor;
    private final DoorPos doorPos;
    private ImmutableSet<DoorPos> corners = ImmutableSet.of();

    public boolean isRoom() {
        return corners.size() == 4;
    }

    public ImmutableSet<DoorPos> getCorners() {
        return corners;
    }

    public DoorPos getDoorPos() {
        return doorPos;
    }

    public interface WallDetector {
        boolean IsWall(DoorPos dp);
    }

    public RoomDetector(
            DoorPos dp,
            int maxDistanceFromDoor
    ) {
        this.doorPos = dp;
        this.maxDistFromDoor = maxDistanceFromDoor;
    }

    public void update(WallDetector wd) {
        Optional<XWall> wall = findEastWestWall(wd, doorPos);
        if (wall.isPresent()) {
            Optional<XWall> zWalls = findNorthSouthWalls(wd, wall.get());
            zWalls.ifPresent(xWall -> {
                if (zWalls.get().eastCorner.z != zWalls.get().westCorner.z) {
                    return;
                }

                if (isConnected(xWall, wd)) {
                    this.corners = ImmutableSet.of(
                            wall.get().westCorner,
                            wall.get().eastCorner,
                            xWall.eastCorner,
                            xWall.westCorner
                    );
                    return;
                }

                boolean connected = false;
                if (xWall.eastCorner.z > wall.get().eastCorner.z) {
                    for (int i = xWall.eastCorner.z - 1; i > wall.get().eastCorner.z; i--) {
                        XWall shifted = new XWall(xWall.westCorner.WithZ(i), xWall.eastCorner.WithZ(i));
                        if (isConnected(shifted, wd)) {
                            connected = true;
                            xWall = shifted;
                            break;
                        }
                    }
                } else {
                    for (int i = wall.get().eastCorner.z - 1; i > xWall.eastCorner.z; i--) { // TODO: Unit test for reverse comparison and --
                        XWall shifted = new XWall(xWall.westCorner.WithZ(i), xWall.eastCorner.WithZ(i));
                        if (isConnected(shifted, wd)) {
                            connected = true;
                            xWall = shifted;
                            break;
                        }
                    }
                }

                if (!connected) {
                    return;
                }

                this.corners = ImmutableSet.of(
                        wall.get().westCorner,
                        wall.get().eastCorner,
                        xWall.eastCorner,
                        xWall.westCorner
                );
            });
        }
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

    private Optional<XWall> findNorthSouthWalls(
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

    private Optional<XWall> findEastWestWall(
            WallDetector wd,
            DoorPos doorPos
    ) {
        int westCornerX = Integer.MAX_VALUE, eastCornerX = -Integer.MAX_VALUE;
        boolean started = false;
        for (int i = 0; i < maxDistFromDoor; i++) {
            DoorPos op = doorPos.offset(i, 0, 0);
            if (wd.IsWall(op)) {
                started = true;
                westCornerX = Math.min(westCornerX, op.x);
                eastCornerX = Math.max(eastCornerX, op.x);
            } else if (started) {
                break;
            }
        }
        for (int i = 0; i < maxDistFromDoor; i++) {
            DoorPos op = doorPos.offset(-i, 0, 0);
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
