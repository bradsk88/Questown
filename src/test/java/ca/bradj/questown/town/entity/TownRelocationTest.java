package ca.bradj.questown.town.entity;

import ca.bradj.questown.town.entity.TownRelocation.RelocationResult;
import ca.bradj.questown.town.rooms.TownPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * Fast regression net for the pure decisions inside flag relocation (ADR-0009, #199). The
 * world-bound copy/destroy/re-activate path is covered by the {@code flag/relocate_nearby} autotest;
 * here we lock the two genuinely-pure pieces: fixture re-anchoring (B1) and precondition validation
 * (B2).
 */
class TownRelocationTest {

    // ---- B1: planFixtureRebase — every registered fixture keeps its absolute world position ----

    @Test
    void rebaseAllFixturesPreservesAbsolutePositionsWhenFlagMovesUp() {
        int oldFlagY = 64;
        int newFlagY = 70; // flag moves up 6
        List<TownPosition> fixtures = List.of(
                new TownPosition(10, 20, 0),   // door at abs Y 64
                new TownPosition(-5, 8, 3),    // fence gate at abs Y 67
                new TownPosition(100, -40, -2) // heal spot at abs Y 62
        );

        List<TownPosition> rebased = TownRelocation.planFixtureRebase(oldFlagY, newFlagY, fixtures);

        Assertions.assertEquals(fixtures.size(), rebased.size());
        for (int i = 0; i < fixtures.size(); i++) {
            TownPosition before = fixtures.get(i);
            TownPosition after = rebased.get(i);
            Assertions.assertEquals(before.x, after.x, "X is absolute — unchanged");
            Assertions.assertEquals(before.z, after.z, "Z is absolute — unchanged");
            Assertions.assertEquals(
                    before.getY(oldFlagY), after.getY(newFlagY),
                    "absolute world Y must survive the move"
            );
        }
    }

    @Test
    void rebaseChangesScanLevelWhenFlagYChanges() {
        TownPosition door = new TownPosition(1, 2, 0);
        List<TownPosition> rebased = TownRelocation.planFixtureRebase(64, 60, List.of(door));
        Assertions.assertEquals(4, rebased.get(0).scanLevel, "scanLevel re-anchored to the lower flag");
        Assertions.assertNotEquals(door.scanLevel, rebased.get(0).scanLevel);
    }

    @Test
    void rebaseToSameFlagYIsIdentity() {
        List<TownPosition> fixtures = List.of(new TownPosition(7, 7, 5), new TownPosition(-1, 0, -3));
        Assertions.assertEquals(fixtures, TownRelocation.planFixtureRebase(64, 64, fixtures));
    }

    @Test
    void rebaseEmptyFixturesIsEmpty() {
        Assertions.assertTrue(TownRelocation.planFixtureRebase(64, 70, List.of()).isEmpty());
    }

    // ---- B2: validate — the precondition decision (same-dimension, well-formed reference) ----

    private static final UUID TOWN = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final BlockPos FLAG = new BlockPos(0, 64, 0);
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");

    @Test
    void validateAcceptsSameDimensionWellFormedReference() {
        Assertions.assertEquals(
                RelocationResult.OK,
                TownRelocation.validate(TOWN, FLAG, OVERWORLD, OVERWORLD)
        );
    }

    @Test
    void validateRejectsCrossDimension() {
        Assertions.assertEquals(
                RelocationResult.CROSS_DIMENSION,
                TownRelocation.validate(TOWN, FLAG, OVERWORLD, NETHER)
        );
    }

    @Test
    void validateRejectsMissingTownUuid() {
        Assertions.assertEquals(
                RelocationResult.MALFORMED_REFERENCE,
                TownRelocation.validate(null, FLAG, OVERWORLD, OVERWORLD)
        );
    }

    @Test
    void validateRejectsMissingFlagPos() {
        Assertions.assertEquals(
                RelocationResult.MALFORMED_REFERENCE,
                TownRelocation.validate(TOWN, null, OVERWORLD, OVERWORLD)
        );
    }

    @Test
    void validateRejectsMissingDimension() {
        Assertions.assertEquals(
                RelocationResult.MALFORMED_REFERENCE,
                TownRelocation.validate(TOWN, FLAG, null, OVERWORLD)
        );
    }

    @Test
    void validateChecksMalformedBeforeDimensionMismatch() {
        // A malformed reference can't have a meaningful dimension comparison — malformed wins.
        Assertions.assertEquals(
                RelocationResult.MALFORMED_REFERENCE,
                TownRelocation.validate(null, FLAG, null, NETHER)
        );
    }

    // ---- B3: fixturesBeyondRadius — the "too far to come with you" split (Phase 4) ----

    // The squared-distance cutoff the ticker uses (Config default): radius 100 → 100^2.
    private static final long RADIUS_SQ = 100L * 100L;
    private static final int FLAG_Y = 64;

    @Test
    void fixturesWithinRadiusAreNotBeyond() {
        BlockPos target = new BlockPos(0, FLAG_Y, 0);
        TownPosition nearDoor = new TownPosition(40, 30, 0); // dist^2 = 1600+900 = 2500 < 10000
        Assertions.assertTrue(
                TownRelocation.fixturesBeyondRadius(target, FLAG_Y, List.of(nearDoor), RADIUS_SQ).isEmpty()
        );
    }

    @Test
    void fixturesBeyondRadiusAreReturned() {
        BlockPos target = new BlockPos(0, FLAG_Y, 0);
        TownPosition farDoor = new TownPosition(200, 0, 0); // dist^2 = 40000 > 10000
        Assertions.assertEquals(
                List.of(farDoor),
                TownRelocation.fixturesBeyondRadius(target, FLAG_Y, List.of(farDoor), RADIUS_SQ)
        );
    }

    @Test
    void fixturesBeyondRadiusSplitsNearFromFar() {
        BlockPos target = new BlockPos(0, FLAG_Y, 0);
        TownPosition near = new TownPosition(10, 10, 0);
        TownPosition nearVertical = new TownPosition(0, 0, 5); // 5 above flag — still in range
        TownPosition farXZ = new TownPosition(150, 0, 0);
        List<TownPosition> beyond = TownRelocation.fixturesBeyondRadius(
                target, FLAG_Y, List.of(near, nearVertical, farXZ), RADIUS_SQ
        );
        Assertions.assertEquals(List.of(farXZ), beyond, "only the out-of-range fixture is dropped");
    }

    @Test
    void verticalDistanceCountsTowardRadius() {
        // scanLevel feeds the absolute Y, so a fixture far in Y alone is still "beyond".
        BlockPos target = new BlockPos(0, FLAG_Y, 0);
        TownPosition highDoor = new TownPosition(0, 0, 150); // scanLevel 150 → abs Y 214, 150 above target
        Assertions.assertEquals(
                List.of(highDoor),
                TownRelocation.fixturesBeyondRadius(target, FLAG_Y, List.of(highDoor), RADIUS_SQ)
        );
    }

    @Test
    void radiusBoundaryIsInclusive() {
        // Exactly at the radius is NOT beyond (matches the ticker's <= cutoff).
        BlockPos target = new BlockPos(0, FLAG_Y, 0);
        TownPosition onRing = new TownPosition(100, 0, 0); // dist^2 == 10000
        Assertions.assertTrue(
                TownRelocation.fixturesBeyondRadius(target, FLAG_Y, List.of(onRing), RADIUS_SQ).isEmpty()
        );
    }
}
