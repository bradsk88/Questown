package ca.bradj.questown.core.space;

import ca.bradj.questown.adapter.Positions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

class InclusiveSpaceTest {

    @Test
    void testGetInclusiveSpaceWithEmptyCollection() {
        Collection<Position> doors = new ArrayList<>();
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Positions.getInclusiveSpace(doors);
        });
    }

    @Test
    void testGetInclusiveSpaceWithSingleDoor() {
        Collection<Position> doors = Arrays.asList(new Position(0, 0, 0));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Positions.getInclusiveSpace(doors);
        });
    }

    @Test
    void testGetInclusiveSpaceWithTwoSameDoors() {
        Collection<Position> doors = Arrays.asList(new Position(1, 1, 1), new Position(1, 1, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            Positions.getInclusiveSpace(doors);
        });
    }

    @Test
    void testGetInclusiveSpaceWithTwoDoors() {
        Collection<Position> doors = Arrays.asList(new Position(1, 1, 1), new Position(2, 2, 2));
        InclusiveSpace space = Positions.getInclusiveSpace(doors);
        Assertions.assertEquals(new InclusiveSpace(new Position(1, 1, 1), new Position(2, 2, 2)), space);
    }

    @Test
    void testGetInclusiveSpaceWithMultipleDoors() {
        Collection<Position> doors = Arrays.asList(
                new Position(1, 2, 3),
                new Position(4, 5, 6),
                new Position(0, -1, -2),
                new Position(7, 8, 9)
        );
        InclusiveSpace space = Positions.getInclusiveSpace(doors);
        Assertions.assertEquals(new InclusiveSpace(new Position(0, -1, -2), new Position(7, 8, 9)), space);
    }

}