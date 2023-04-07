package ca.bradj.questown.logic;

import ca.bradj.questown.rooms.DoorPos;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoorFinderTest {

    @Test
    public void Test() {

        boolean[][] map = {
                {true, false, false, true},
                {false, true, false, false},
                {false, false, true, false},
                {false, false, false, false}
        };

        ImmutableList<DoorPos> expected = ImmutableList.of(
                new DoorPos(0, 0, 0),
                new DoorPos(3, 0, 0),
                new DoorPos(1, 0, 1),
                new DoorPos(2, 0, 2)
        );

        Collection<DoorPos> dps = DoorFinder.LocateDoorsAroundPosition(
                new DoorPos(0, 0, 0),
                (DoorPos dp) -> {
                    if (dp.x < 0 || dp.z < 0) {
                        return false;
                    }
                    if (dp.x >= map[0].length || dp.z >= map.length) {
                        return false;
                    }
                    return map[dp.z][dp.x];
                },
                5 // Random
        );
        assertEquals(expected, dps);
    }

}