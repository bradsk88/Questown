package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import ca.bradj.questown.rooms.ZWall;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WallDetectionTest {

    @Test
    public void Test_DetectNorthToSouthWall() {
        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W"},
                {"D"},
                {"W"}
        };

        Optional<ZWall> wall = WallDetection.findNorthToSouthWall(4, (Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        }, new Position(0, 0, 1));

        assertTrue(wall.isPresent());
        assertEquals(0, wall.get().northCorner.x);
        assertEquals(0, wall.get().northCorner.z);
        assertEquals(0, wall.get().southCorner.x);
        assertEquals(2, wall.get().southCorner.z);

    }

    @Test
    public void Test_DetectEastOrWestWall_EastWall() {
        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W"},
                {"D", "A", "W"},
                {"W", "W", "W"},
        };

        ZWall doorWall = new ZWall(
                new Position(0, 0, 0),
                new Position(0, 0, 2)
        );

        Optional<ZWall> wall = WallDetection.findEastOrWestWall(4, (Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        }, doorWall);

        assertTrue(wall.isPresent());
        assertEquals(2, wall.get().northCorner.x);
        assertEquals(0, wall.get().northCorner.z);
        assertEquals(2, wall.get().southCorner.x);
        assertEquals(2, wall.get().southCorner.z);
    }

    @Test
    public void Test_DetectEastOrWestWall_WestWall() {
        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W"},
                {"W", "A", "D"},
                {"W", "W", "W"},
        };

        ZWall doorWall = new ZWall(
                new Position(2, 0, 0),
                new Position(2, 0, 2)
        );

        Optional<ZWall> wall = WallDetection.findEastOrWestWall(4, (Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        }, doorWall);

        assertTrue(wall.isPresent());
        assertEquals(0, wall.get().northCorner.x);
        assertEquals(0, wall.get().northCorner.z);
        assertEquals(0, wall.get().southCorner.x);
        assertEquals(2, wall.get().southCorner.z);
    }
}