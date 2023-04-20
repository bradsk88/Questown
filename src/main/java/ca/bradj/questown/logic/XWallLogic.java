package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import ca.bradj.questown.rooms.XWall;

import java.util.Optional;

public class XWallLogic {
    public static boolean isConnected(XWall wall, RoomDetector.WallDetector wd) {
        for (int i = wall.westCorner.x; i < wall.eastCorner.x; i++) {
            if (wd.IsWall(wall.westCorner.offset(i, 0, 0))) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static Optional<XWall> eastFromCorner(
            RoomDetector.WallDetector wd,
            Position northCorner,
            int maxDistFromDoor
    ) {
        int eastX = northCorner.x;
        for (int i = 0; i < maxDistFromDoor; i++) {
            if (!wd.IsWall(northCorner.WithX(northCorner.x + i))) {
                break;
            }
            eastX = northCorner.x + i;
        }
        if (eastX == northCorner.x)
            return Optional.empty();
        return Optional.of(
                new XWall(northCorner, northCorner.WithX(eastX))
        );
    }
    public static Optional<XWall> westFromCorner(
            RoomDetector.WallDetector wd,
            Position northCorner,
            int maxDistFromDoor
    ) {
        int westX = northCorner.x;
        for (int i = 0; i > -maxDistFromDoor; i--) {
            if (!wd.IsWall(northCorner.WithX(northCorner.x + i))) {
                break;
            }
            westX = northCorner.x + i;
        }
        if (westX == northCorner.x)
            return Optional.empty();
        return Optional.of(
                new XWall(northCorner.WithX(westX), northCorner)
        );
    }
}
