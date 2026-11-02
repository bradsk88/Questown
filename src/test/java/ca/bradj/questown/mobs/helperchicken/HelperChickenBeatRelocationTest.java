package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** The chicken's lesson targets move with a relocated flag, since each is the flag position plus a rotated offset. */
class HelperChickenBeatRelocationTest {

    private static final BlockPos OLD_FLAG = new BlockPos(100, 64, 200);
    private static final BlockPos NEW_FLAG = new BlockPos(-40, 70, 15);

    @Test
    void everyBeatTargetTranslatesByTheFlagDelta() {
        BlockPos delta = NEW_FLAG.subtract(OLD_FLAG);
        for (ChickenBeatState state : ChickenBeatState.values()) {
            for (Rotation rotation : Rotation.values()) {
                BlockPos before = HelperChickenBeatOffsets.resolveTarget(state, OLD_FLAG, rotation);
                BlockPos after = HelperChickenBeatOffsets.resolveTarget(state, NEW_FLAG, rotation);
                if (before == null) {
                    Assertions.assertNull(
                            after,
                            "target-less beat " + state + " (" + rotation + ") must stay target-less after the move"
                    );
                    continue;
                }
                Assertions.assertEquals(
                        delta, after.subtract(before),
                        "beat " + state + " (" + rotation + ") must follow the flag by exactly the move delta"
                );
            }
        }
    }

    @Test
    void everyStandTargetTranslatesByTheFlagDelta() {
        BlockPos delta = NEW_FLAG.subtract(OLD_FLAG);
        for (ChickenBeatState state : ChickenBeatState.values()) {
            for (Rotation rotation : Rotation.values()) {
                BlockPos before = HelperChickenBeatOffsets.resolveStandTarget(state, OLD_FLAG, rotation);
                BlockPos after = HelperChickenBeatOffsets.resolveStandTarget(state, NEW_FLAG, rotation);
                if (before == null) {
                    Assertions.assertNull(after, "target-less stand beat " + state + " (" + rotation + ")");
                    continue;
                }
                Assertions.assertEquals(
                        delta, after.subtract(before),
                        "stand beat " + state + " (" + rotation + ") must follow the flag"
                );
            }
        }
    }

    @Test
    void targetlessBeatsHaveNoTargetRegardlessOfFlagPosition() {
        // Lessons with no place stay null wherever the flag is.
        for (ChickenBeatState state : new ChickenBeatState[]{
                ChickenBeatState.SUNSET_AND_MAP,
                ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY,
                ChickenBeatState.COMPLETE,
                ChickenBeatState.FORFEIT
        }) {
            Assertions.assertNull(HelperChickenBeatOffsets.resolveTarget(state, NEW_FLAG, Rotation.NONE));
            Assertions.assertNull(HelperChickenBeatOffsets.resolveStandTarget(state, NEW_FLAG, Rotation.NONE));
        }
    }

    @Test
    void carriedRotationDeterminesOrientationSoItMustRideAlong() {
        // A rotated offset lands elsewhere, which is why the rotation travels with the town.
        BlockPos none = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_WALL_BLOCK, NEW_FLAG, Rotation.NONE
        );
        BlockPos ninety = HelperChickenBeatOffsets.resolveTarget(
                ChickenBeatState.WAITING_FOR_WALL_BLOCK, NEW_FLAG, Rotation.CLOCKWISE_90
        );
        Assertions.assertNotEquals(
                none, ninety,
                "an asymmetric offset must resolve differently per rotation — so the carried rotation matters"
        );
    }

    @Test
    void flagBeatSitsExactlyOnTheFlagAfterTheMove() {
        // The stick lesson's target is the flag itself, under every rotation.
        for (Rotation rotation : Rotation.values()) {
            Assertions.assertEquals(
                    NEW_FLAG,
                    HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_STICK, NEW_FLAG, rotation),
                    "the stick beat pecks the flag itself, wherever the flag now sits"
            );
        }
    }
}
