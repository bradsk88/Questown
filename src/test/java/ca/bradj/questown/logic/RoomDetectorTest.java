package ca.bradj.questown.logic;

import ca.bradj.questown.core.space.Position;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class RoomDetectorTest {

    @Test
    public void Test_DetectSimpleRoom_N() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "D", "W", "A"},
                {"W", "A", "W", "A"},
                {"W", "W", "W", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 0), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());

    }
@Test
    public void Test_DetectSimpleRoom_E() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W", "A"},
                {"W", "A", "D", "A"},
                {"W", "W", "W", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 0), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());

    }
@Test
    public void Test_DetectSimpleRoom_S() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W", "A"},
                {"W", "A", "W", "A"},
                {"W", "D", "W", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 0), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());

    }
@Test
    public void Test_DetectSimpleRoom_W() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W", "A"},
                {"D", "A", "W", "A"},
                {"W", "W", "W", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 0), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());

    }

    @Test
    public void Test_DetectIncompleteRoom() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "D", "W", "A"},
                {"W", "A", "W", "A"},
                {"W", "W", "A", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 0), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertFalse(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of();
        assertEquals(expectedCorners, rd.getCorners());

    }

    @Test
    public void Test_DetectSimpleRoomDestroyed() {
        // A = air
        // W = wall
        // D = door
        String[][] map1 = {
                {"W", "D", "W", "A"},
                {"W", "A", "W", "A"},
                {"W", "W", "W", "A"}
        };
        String[][] map2 = {
                {"W", "D", "W", "A"},
                {"W", "A", "W", "A"},
                {"W", "W", "A", "A"}
        };

        Function<String[][], Predicate<Position>> wdFn = (String[][] map) -> (Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        };


        RoomDetector rd = new RoomDetector(new Position(1, 0, 0), 4);

        rd.update(position -> wdFn.apply(map1).test(position));
        assertTrue(rd.isRoom());

        rd.update(position -> wdFn.apply(map2).test(position));
        assertFalse(rd.isRoom());

    }

    @Test
    public void Test_DetectSimpleRoomWithAirAround() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"A", "A", "A", "A", "A"},
                {"A", "W", "D", "W", "A"},
                {"A", "W", "A", "W", "A"},
                {"A", "W", "W", "W", "A"},
                {"A", "A", "A", "A", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 1), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(1, 0, 1),
                new Position(3, 0, 1),
                new Position(3, 0, 3),
                new Position(1, 0, 3)
        );
        assertEquals(expectedCorners, rd.getCorners());

    }
    @Test
    public void Test_DetectSimpleRoomWithAirAndWallAround() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"A", "A", "A", "A", "A", "A"},
                {"W", "A", "W", "D", "W", "A"},
                {"A", "A", "W", "A", "W", "A"},
                {"A", "A", "W", "W", "W", "A"},
                {"A", "A", "A", "A", "A", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(3, 0, 1), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(2, 0, 1),
                new Position(4, 0, 1),
                new Position(4, 0, 3),
                new Position(2, 0, 3)
        );
        assertEquals(expectedCorners, rd.getCorners());

    }
    @Test
    public void Test_DetectOpenSouthWall() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"A", "A", "A", "A", "A", "A"},
                {"W", "A", "W", "D", "W", "A"},
                {"A", "A", "W", "A", "W", "A"},
                {"A", "A", "W", "A", "W", "A"},
                {"A", "A", "A", "A", "A", "A"}
        };

        RoomDetector rd = new RoomDetector(new Position(3, 0, 1), 4);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertFalse(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of();
        assertEquals(expectedCorners, rd.getCorners());

    }
    @Test
    public void Test_DetectShapeLikeLetterA() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "D", "W"},
                {"W", "A", "W"},
                {"W", "W", "W"},
                {"W", "A", "W"},
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 0), 5);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());
    }
    @Test
    public void Test_DetectShapeLikeLetterA_90() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W", "W"},
                {"D", "A", "W", "A"},
                {"W", "W", "W", "W"},
        };

        RoomDetector rd = new RoomDetector(new Position(0, 0, 1), 5);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());
    }
    @Test
    public void Test_DetectShapeLikeLetterA_180() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "A", "W"},
                {"W", "W", "W"},
                {"W", "A", "W"},
                {"W", "D", "W"},
        };

        RoomDetector rd = new RoomDetector(new Position(1, 0, 3), 5);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());
    }
    @Test
    public void Test_DetectShapeLikeLetterA_270() {

        // A = air
        // W = wall
        // D = door
        String[][] map = {
                {"W", "W", "W", "W"},
                {"A", "W", "A", "D"},
                {"W", "W", "W", "W"},
        };

        RoomDetector rd = new RoomDetector(new Position(3, 0, 1), 5);
        rd.update((Position dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "W".equals(map[dp.z][dp.x]) || "D".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<Position> expectedCorners = ImmutableSet.of(
                new Position(0, 0, 0),
                new Position(2, 0, 0),
                new Position(2, 0, 2),
                new Position(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());
    }
 // TODO: Rotate rooms 90
}