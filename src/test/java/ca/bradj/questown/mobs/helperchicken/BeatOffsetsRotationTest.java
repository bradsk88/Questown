package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BeatOffsetsRotationTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // resolveTarget for beat states WITH a positional target
    // -------------------------------------------------------------------------

    @Test
    void resolveTarget_waitingForWandOnCampfire_rotationNone_equalsCampfireOffset() {
        BlockPos result = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                BlockPos.ZERO,
                Rotation.NONE
        );
        Assertions.assertEquals(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, result);
    }

    @Test
    void resolveTarget_waitingForWandOnCampfire_rotationClockwise90_matchesRotatedOffset() {
        BlockPos result = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                BlockPos.ZERO,
                Rotation.CLOCKWISE_90
        );
        Assertions.assertEquals(
                HelperChickenBeatOffsets.CAMPFIRE_OFFSET.rotate(Rotation.CLOCKWISE_90),
                result
        );
    }

    @Test
    void resolveTarget_arbitraryFlagPos_rotationClockwise180_combinesFlagPosAndRotatedOffset() {
        BlockPos flagPos = new BlockPos(100, 64, 200);
        BlockPos result = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                flagPos,
                Rotation.CLOCKWISE_180
        );
        BlockPos expected = flagPos.offset(
                HelperChickenBeatOffsets.CAMPFIRE_OFFSET.rotate(Rotation.CLOCKWISE_180)
        );
        Assertions.assertEquals(expected, result);
    }

    @Test
    void resolveTarget_eachRotation_producesDistinctMath() {
        // Even with current BlockPos.ZERO placeholders, the math should still apply
        // the rotation on the offset BEFORE adding flagPos. Validate the composition rule.
        BlockPos flagPos = new BlockPos(10, 80, -20);
        for (Rotation r : Rotation.values()) {
            BlockPos expected = flagPos.offset(HelperChickenBeatOffsets.CAMPFIRE_OFFSET.rotate(r));
            BlockPos actual = HelperChickenBeatOffsets.resolveTarget(
                    ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE,
                    flagPos,
                    r
            );
            Assertions.assertEquals(expected, actual, "Rotation " + r + " mismatch");
        }
    }

    @Test
    void resolveTarget_beatStatesMappingToWallBlockDoorSignChest_useMatchingOffsets() {
        BlockPos flagPos = new BlockPos(50, 70, 50);
        Rotation r = Rotation.CLOCKWISE_90;

        Assertions.assertEquals(
                flagPos.offset(HelperChickenBeatOffsets.WALL_BLOCK_OFFSET.rotate(r)),
                HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_WALL_BLOCK, flagPos, r)
        );
        Assertions.assertEquals(
                flagPos.offset(HelperChickenBeatOffsets.DOOR_OFFSET.rotate(r)),
                HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_DOOR, flagPos, r)
        );
        Assertions.assertEquals(
                flagPos.offset(HelperChickenBeatOffsets.DOOR_OFFSET.rotate(r)),
                HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_WAND_ON_DOOR, flagPos, r)
        );
        Assertions.assertEquals(
                flagPos.offset(HelperChickenBeatOffsets.SIGN_OFFSET.rotate(r)),
                HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_SIGN, flagPos, r)
        );
        Assertions.assertEquals(
                flagPos.offset(HelperChickenBeatOffsets.CHEST_OFFSET.rotate(r)),
                HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_CHEST, flagPos, r)
        );
        Assertions.assertEquals(
                flagPos.offset(HelperChickenBeatOffsets.GATE_CENTER_OFFSET.rotate(r)),
                HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, flagPos, r)
        );
    }

    // -------------------------------------------------------------------------
    // Null-return for beat states WITHOUT a positional target
    // -------------------------------------------------------------------------

    @Test
    void resolveTarget_complete_returnsNull() {
        Assertions.assertNull(
                HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.COMPLETE,
                        BlockPos.ZERO,
                        Rotation.NONE
                )
        );
    }

    @Test
    void resolveTarget_forfeit_returnsNull() {
        Assertions.assertNull(
                HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.FORFEIT,
                        BlockPos.ZERO,
                        Rotation.NONE
                )
        );
    }

    @Test
    void resolveTarget_sunsetAndMap_returnsNull() {
        Assertions.assertNull(
                HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.SUNSET_AND_MAP,
                        BlockPos.ZERO,
                        Rotation.NONE
                )
        );
    }

    @Test
    void resolveTarget_awaitingWorldlySeedsDelivery_returnsNull() {
        Assertions.assertNull(
                HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY,
                        BlockPos.ZERO,
                        Rotation.NONE
                )
        );
    }
}
