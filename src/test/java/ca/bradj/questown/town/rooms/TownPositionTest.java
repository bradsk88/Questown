package ca.bradj.questown.town.rooms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The fixture Y-rebase used by flag relocation (ADR-0009, #199): a registered fixture's absolute
 * world Y must survive moving the flag to a new origin, since the physical block stays put.
 */
class TownPositionTest {

    @Test
    void rebasePreservesAbsoluteYWhenFlagMovesDown() {
        // Fixture sits 3 above a flag at Y=64 → absolute Y 67. Move the flag down to Y=60.
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
        TownPosition fixture = new TownPosition(-5, 8, -2); // absolute Y = 70 + (-2) = 68
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
