package ca.bradj.questown.town.rooms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** A fixture keeps its world Y when the flag moves to a new height. */
class TownPositionTest {

    @Test
    void rebasePreservesAbsoluteYWhenFlagMovesDown() {
        TownPosition fixture = new TownPosition(10, 20, 3);
        Assertions.assertEquals(67, fixture.getY(64));

        TownPosition rebased = fixture.rebasedTo(64, 60);
        Assertions.assertEquals(67, rebased.getY(60), "absolute Y must be preserved");
        Assertions.assertEquals(7, rebased.scanLevel, "scanLevel re-anchored to the new flag Y");
        Assertions.assertEquals(10, rebased.x);
        Assertions.assertEquals(20, rebased.z);
    }

    @Test
    void rebasePreservesAbsoluteYWhenFlagMovesUp() {
        TownPosition fixture = new TownPosition(-5, 8, -2);
        Assertions.assertEquals(68, fixture.getY(70));

        TownPosition rebased = fixture.rebasedTo(70, 80);
        Assertions.assertEquals(68, rebased.getY(80));
        Assertions.assertEquals(-12, rebased.scanLevel);
    }

    @Test
    void rebaseToSameYIsIdentity() {
        TownPosition fixture = new TownPosition(1, 2, 5);
        Assertions.assertEquals(fixture, fixture.rebasedTo(64, 64));
    }
}
