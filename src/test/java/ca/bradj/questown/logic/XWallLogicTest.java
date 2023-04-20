package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import ca.bradj.questown.rooms.XWall;
import ca.bradj.questown.rooms.ZWall;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class XWallLogicTest {

    @Test
    void Test_EastFromCorner_Simple() {
        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W"},
        };

        Optional<XWall> wall = XWallLogic.eastFromCorner((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        }, new Position(0, 0, 0), 4);

        assertTrue(wall.isPresent());
        assertEquals(0, wall.get().westCorner.x);
        assertEquals(0, wall.get().westCorner.z);
        assertEquals(2, wall.get().eastCorner.x);
        assertEquals(0, wall.get().eastCorner.z);
    }
    @Test
    void Test_WestFromCorner_Simple() {
        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W"},
        };

        Optional<XWall> wall = XWallLogic.westFromCorner((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        }, new Position(2, 0, 0), 4);

        assertTrue(wall.isPresent());
        assertEquals(0, wall.get().westCorner.x);
        assertEquals(0, wall.get().westCorner.z);
        assertEquals(2, wall.get().eastCorner.x);
        assertEquals(0, wall.get().eastCorner.z);
    }

}