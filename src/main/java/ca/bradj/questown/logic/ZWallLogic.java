package ca.bradj.questown.logic;

import ca.bradj.questown.rooms.ZWall;

public class ZWallLogic {

    public static boolean isConnected(ZWall wall, RoomDetector.WallDetector wd) {
        for (int i = wall.northCorner.z; i < wall.southCorner.z; i++) {
            if (wd.IsWall(wall.northCorner.offset(0, 0, i))) {
                continue;
            }
            return false;
        }
        return true;
    }

}
