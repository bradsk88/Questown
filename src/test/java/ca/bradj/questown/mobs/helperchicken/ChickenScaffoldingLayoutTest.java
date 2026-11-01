package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ChickenScaffoldingLayoutTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // NONE rotation — authored base layout
    // -------------------------------------------------------------------------

    @Test
    void forRotation_none_containsUnlitCampfireAtCampfireOffset() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);

        ChickenScaffoldingLayout.BlockPlacement campfire = findAt(
                base, HelperChickenBeatOffsets.CAMPFIRE_OFFSET
        );
        Assertions.assertNotNull(campfire, "campfire must be placed at CAMPFIRE_OFFSET");
        Assertions.assertTrue(
                campfire.blockState().is(Blocks.CAMPFIRE),
                "block at CAMPFIRE_OFFSET must be a campfire"
        );
        Assertions.assertFalse(
                campfire.blockState().getValue(CampfireBlock.LIT),
                "campfire must be unlit so wand-light can observe the transition"
        );
    }

    @Test
    void forRotation_none_leavesWallBlockOffsetEmpty() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        Assertions.assertNull(
                findAt(base, HelperChickenBeatOffsets.WALL_BLOCK_OFFSET),
                "WALL_BLOCK_OFFSET is the player-completes-the-wall gap — scaffolding must leave it open"
        );
    }

    @Test
    void forRotation_none_leavesDoorOffsetEmpty() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        Assertions.assertNull(
                findAt(base, HelperChickenBeatOffsets.DOOR_OFFSET),
                "DOOR_OFFSET is the player-places-door gap — scaffolding must leave it open"
        );
    }

    @Test
    void forRotation_none_leavesGateCenterEmpty() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        Assertions.assertNull(
                findAt(base, HelperChickenBeatOffsets.GATE_CENTER_OFFSET),
                "GATE_CENTER_OFFSET is the passage between gate columns — must stay open"
        );
    }

    @Test
    void forRotation_none_placesTwoOakFencePostsFlankingGateCenter() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        BlockPos center = HelperChickenBeatOffsets.GATE_CENTER_OFFSET;
        BlockPos west = new BlockPos(center.getX() - 1, center.getY(), center.getZ());
        BlockPos east = new BlockPos(center.getX() + 1, center.getY(), center.getZ());

        ChickenScaffoldingLayout.BlockPlacement w = findAt(base, west);
        ChickenScaffoldingLayout.BlockPlacement e = findAt(base, east);
        Assertions.assertNotNull(w, "fence column missing at west of gate");
        Assertions.assertNotNull(e, "fence column missing at east of gate");
        Assertions.assertTrue(w.blockState().is(Blocks.OAK_FENCE));
        Assertions.assertTrue(e.blockState().is(Blocks.OAK_FENCE));
    }

    @Test
    void forRotation_none_perimeterCobblestoneCoversExpectedCount() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);

        long cobbleCount = base.stream()
                .filter(p -> p.blockState().is(Blocks.COBBLESTONE))
                .count();

        // 5x5 footprint perimeter = 16 columns × 2 rows (y=0, y=1) = 32 positions,
        // minus WALL_BLOCK_OFFSET (1 gap at y=0) and DOOR_OFFSET column (2 gaps, y=0 and y=1) = 29.
        Assertions.assertEquals(
                29L,
                cobbleCount,
                "2-high perimeter (32 blocks) minus 1 wall gap at y=0 and 2 door-column gaps"
        );
    }

    @Test
    void forRotation_none_allCobblestoneAreOnPerimeter() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        for (ChickenScaffoldingLayout.BlockPlacement p : base) {
            if (!p.blockState().is(Blocks.COBBLESTONE)) {
                continue;
            }
            BlockPos pos = p.offset();
            boolean onPerimeter = pos.getX() == 2 || pos.getX() == 6
                    || pos.getZ() == 2 || pos.getZ() == 6;
            Assertions.assertTrue(
                    onPerimeter,
                    "cobblestone placement " + pos + " must be on the perimeter"
            );
            Assertions.assertTrue(pos.getX() >= 2 && pos.getX() <= 6);
            Assertions.assertTrue(pos.getZ() >= 2 && pos.getZ() <= 6);
            Assertions.assertTrue(pos.getY() == 0 || pos.getY() == 1,
                    "walls are authored at y=0 and y=1 (2-high for RoomRecipes registration)");
        }
    }

    @Test
    void forRotation_none_wallGapAtY0ButTopAtY1Present() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        BlockPos wallGap = HelperChickenBeatOffsets.WALL_BLOCK_OFFSET;
        Assertions.assertNull(
                findAt(base, wallGap),
                "player-fills gap at WALL_BLOCK_OFFSET (y=0)"
        );
        Assertions.assertNotNull(
                findAt(base, new BlockPos(wallGap.getX(), 1, wallGap.getZ())),
                "the top (y=1) at the wall-gap column must still be present — player only fills y=0"
        );
    }

    @Test
    void forRotation_none_doorColumnBothHalvesEmpty() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        BlockPos doorGap = HelperChickenBeatOffsets.DOOR_OFFSET;
        Assertions.assertNull(
                findAt(base, doorGap),
                "door gap at y=0 must be open"
        );
        Assertions.assertNull(
                findAt(base, new BlockPos(doorGap.getX(), 1, doorGap.getZ())),
                "door gap at y=1 must also be open (oak door is 2-high)"
        );
    }

    @Test
    void signAndChestOffsets_landInsideRoomInterior() {
        // The room recipe scan fires only on blocks placed INSIDE a registered
        // room (perimeter walls at x=2/x=6, z=2/z=6 → interior x∈[3..5], z∈[3..5]).
        // If the chicken's peck targets sit on or outside the perimeter the
        // sign-to-job-board conversion never runs and the beat deadlocks.
        BlockPos sign = HelperChickenBeatOffsets.SIGN_OFFSET;
        BlockPos chest = HelperChickenBeatOffsets.CHEST_OFFSET;
        Assertions.assertTrue(
                sign.getX() >= 3 && sign.getX() <= 5
                        && sign.getZ() >= 3 && sign.getZ() <= 5,
                "SIGN_OFFSET " + sign + " must be in room interior x∈[3..5], z∈[3..5]"
        );
        Assertions.assertTrue(
                chest.getX() >= 3 && chest.getX() <= 5
                        && chest.getZ() >= 3 && chest.getZ() <= 5,
                "CHEST_OFFSET " + chest + " must be in room interior x∈[3..5], z∈[3..5]"
        );
        Assertions.assertNotEquals(
                new BlockPos(sign.getX(), 0, sign.getZ()),
                new BlockPos(chest.getX(), 0, chest.getZ()),
                "sign and chest must occupy distinct interior cells"
        );
    }

    @Test
    void forRotation_none_returnsImmutableList() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> base.add(new ChickenScaffoldingLayout.BlockPlacement(
                        BlockPos.ZERO, Blocks.DIRT.defaultBlockState()
                ))
        );
    }

    // -------------------------------------------------------------------------
    // Rotation invariance
    // -------------------------------------------------------------------------

    @Test
    void forRotation_clockwise90_sameSizeAsBase() {
        Assertions.assertEquals(
                ChickenScaffoldingLayout.forRotation(Rotation.NONE).size(),
                ChickenScaffoldingLayout.forRotation(Rotation.CLOCKWISE_90).size()
        );
    }

    @Test
    void forRotation_allFourRotations_produceSameBlockCount() {
        int expected = ChickenScaffoldingLayout.forRotation(Rotation.NONE).size();
        for (Rotation r : Rotation.values()) {
            Assertions.assertEquals(
                    expected,
                    ChickenScaffoldingLayout.forRotation(r).size(),
                    "rotation " + r + " changed placement count"
            );
        }
    }

    @Test
    void forRotation_clockwise90_offsetsMatchManuallyRotatedBase() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        List<ChickenScaffoldingLayout.BlockPlacement> rotated =
                ChickenScaffoldingLayout.forRotation(Rotation.CLOCKWISE_90);

        Set<BlockPos> expectedPositions = new HashSet<>();
        for (ChickenScaffoldingLayout.BlockPlacement p : base) {
            expectedPositions.add(p.offset().rotate(Rotation.CLOCKWISE_90));
        }
        Set<BlockPos> actualPositions = new HashSet<>();
        for (ChickenScaffoldingLayout.BlockPlacement p : rotated) {
            actualPositions.add(p.offset());
        }
        Assertions.assertEquals(expectedPositions, actualPositions);
    }

    @Test
    void forRotation_campfireAtRotatedCampfireOffsetUnderEachRotation() {
        for (Rotation r : Rotation.values()) {
            BlockPos expected = HelperChickenBeatOffsets.CAMPFIRE_OFFSET.rotate(r);
            ChickenScaffoldingLayout.BlockPlacement campfire = findAt(
                    ChickenScaffoldingLayout.forRotation(r), expected
            );
            Assertions.assertNotNull(
                    campfire,
                    "rotation " + r + " must place campfire at rotated CAMPFIRE_OFFSET"
            );
            Assertions.assertTrue(campfire.blockState().is(Blocks.CAMPFIRE));
            Assertions.assertFalse(campfire.blockState().getValue(CampfireBlock.LIT));
        }
    }

    @Test
    void forRotation_clockwise90_wallGapIsAtRotatedWallBlockOffset() {
        List<ChickenScaffoldingLayout.BlockPlacement> rotated =
                ChickenScaffoldingLayout.forRotation(Rotation.CLOCKWISE_90);
        Assertions.assertNull(findAt(
                rotated, HelperChickenBeatOffsets.WALL_BLOCK_OFFSET.rotate(Rotation.CLOCKWISE_90)
        ));
    }

    @Test
    void forRotation_clockwise90_doorGapIsAtRotatedDoorOffset() {
        List<ChickenScaffoldingLayout.BlockPlacement> rotated =
                ChickenScaffoldingLayout.forRotation(Rotation.CLOCKWISE_90);
        Assertions.assertNull(findAt(
                rotated, HelperChickenBeatOffsets.DOOR_OFFSET.rotate(Rotation.CLOCKWISE_90)
        ));
    }

    @Test
    void forRotation_clockwise90_gateColumnsFlankRotatedGateCenter() {
        List<ChickenScaffoldingLayout.BlockPlacement> rotated =
                ChickenScaffoldingLayout.forRotation(Rotation.CLOCKWISE_90);

        BlockPos centerRotated =
                HelperChickenBeatOffsets.GATE_CENTER_OFFSET.rotate(Rotation.CLOCKWISE_90);
        Assertions.assertNull(
                findAt(rotated, centerRotated),
                "gate passage must remain empty after rotation"
        );

        BlockPos baseWest = new BlockPos(
                HelperChickenBeatOffsets.GATE_CENTER_OFFSET.getX() - 1,
                HelperChickenBeatOffsets.GATE_CENTER_OFFSET.getY(),
                HelperChickenBeatOffsets.GATE_CENTER_OFFSET.getZ()
        );
        BlockPos baseEast = new BlockPos(
                HelperChickenBeatOffsets.GATE_CENTER_OFFSET.getX() + 1,
                HelperChickenBeatOffsets.GATE_CENTER_OFFSET.getY(),
                HelperChickenBeatOffsets.GATE_CENTER_OFFSET.getZ()
        );
        Assertions.assertNotNull(findAt(rotated, baseWest.rotate(Rotation.CLOCKWISE_90)));
        Assertions.assertNotNull(findAt(rotated, baseEast.rotate(Rotation.CLOCKWISE_90)));
    }

    // -------------------------------------------------------------------------
    // Directional property rotation
    // -------------------------------------------------------------------------

    @Test
    void forRotation_clockwise90_rotatesCampfireFacingProperty() {
        // Default campfire faces NORTH. CLOCKWISE_90 rotates NORTH → EAST.
        ChickenScaffoldingLayout.BlockPlacement baseCampfire = findAt(
                ChickenScaffoldingLayout.forRotation(Rotation.NONE),
                HelperChickenBeatOffsets.CAMPFIRE_OFFSET
        );
        ChickenScaffoldingLayout.BlockPlacement rotatedCampfire = findAt(
                ChickenScaffoldingLayout.forRotation(Rotation.CLOCKWISE_90),
                HelperChickenBeatOffsets.CAMPFIRE_OFFSET.rotate(Rotation.CLOCKWISE_90)
        );
        Assertions.assertNotNull(baseCampfire);
        Assertions.assertNotNull(rotatedCampfire);

        BlockState expectedRotated =
                baseCampfire.blockState().rotate(Rotation.CLOCKWISE_90);
        Assertions.assertEquals(expectedRotated, rotatedCampfire.blockState());
    }

    // -------------------------------------------------------------------------
    // No duplicate offsets
    // -------------------------------------------------------------------------

    @Test
    void forRotation_none_offsetsAreUnique() {
        List<ChickenScaffoldingLayout.BlockPlacement> base =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        Set<BlockPos> seen = new HashSet<>();
        for (ChickenScaffoldingLayout.BlockPlacement p : base) {
            Assertions.assertTrue(
                    seen.add(p.offset()),
                    "duplicate placement at " + p.offset()
            );
        }
    }

    private static ChickenScaffoldingLayout.BlockPlacement findAt(
            List<ChickenScaffoldingLayout.BlockPlacement> placements,
            BlockPos pos
    ) {
        for (ChickenScaffoldingLayout.BlockPlacement p : placements) {
            if (p.offset().equals(pos)) {
                return p;
            }
        }
        return null;
    }
}
