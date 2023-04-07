package ca.bradj.questown.logic;

import ca.bradj.questown.rooms.DoorPos;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomDetectorTest {

    @Test
    public void Test_DetectSimpleRoom() {

        String[][] map = {
                {"wall", "door", "wall", "air"},
                {"wall", "air", "wall", "air"},
                {"wall", "wall", "wall", "air"}
        };

        RoomDetector rd = new RoomDetector(new DoorPos(1, 0, 0), 4);
        rd.update((DoorPos dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "wall".equals(map[dp.z][dp.x]);
        });
        assertTrue(rd.isRoom());

        ImmutableSet<DoorPos> expectedCorners = ImmutableSet.of(
                new DoorPos(0, 0, 0),
                new DoorPos(2, 0, 0),
                new DoorPos(2, 0, 2),
                new DoorPos(0, 0, 2)
        );
        assertEquals(expectedCorners, rd.getCorners());

    }

    @Test
    public void Test_DetectIncompleteRoom() {

        String[][] map = {
                {"wall", "door", "wall", "air"},
                {"wall", "air", "wall", "air"},
                {"wall", "wall", "air", "air"}
        };

        RoomDetector rd = new RoomDetector(new DoorPos(1, 0, 0), 4);
        rd.update((DoorPos dp) -> {
            if (dp.x < 0 || dp.z < 0) {
                return false;
            }
            if (dp.x >= map[0].length || dp.z >= map.length) {
                return false;
            }
            return "wall".equals(map[dp.z][dp.x]);
        });
        assertFalse(rd.isRoom());

        ImmutableSet<DoorPos> expectedCorners = ImmutableSet.of();
        assertEquals(expectedCorners, rd.getCorners());

    }

}