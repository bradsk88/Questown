package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import ca.bradj.questown.rooms.ZWall;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZWallLogicTest {

    @Test
    void Test_IsConnected_SimpleConnected() {
        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W"},
                {"W"},
                {"W"},
        };
        boolean connected = ZWallLogic.isConnected(
                new ZWall(
                        new Position(0, 0, 0),
                        new Position(0, 0, 2)
                ), (Position dp) -> {
                    if (dp.x < 0 || dp.z < 0) {
                        return false;
                    }
                    if (dp.x >= map[0].length || dp.z >= map.length) {
                        return false;
                    }
                    return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
                }
        );
        assertTrue(connected);
    }
    @Test
    void Test_IsConnected_SimpleBroken() {
        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W"},
                {"A"},
                {"W"},
        };
        boolean connected = ZWallLogic.isConnected(
                new ZWall(
                        new Position(0, 0, 0),
                        new Position(0, 0, 2)
                ), (Position dp) -> {
                    if (dp.x < 0 || dp.z < 0) {
                        return false;
                    }
                    if (dp.x >= map[0].length || dp.z >= map.length) {
                        return false;
                    }
                    return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
                }
        );
        assertFalse(connected);
    }

}