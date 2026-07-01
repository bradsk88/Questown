package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Locks the "the tutorial follows the town" property for flag relocation (#199, Phase 5).
 *
 * <p>The helper-chicken arc needs no special handling when the town flag is relocated:
 * {@link HelperChickenBeatOffsets#resolveTarget} computes every beat target fresh from
 * {@code flagPos + rotate(localOffset)}, and the structure rotation rides along with the
 * flag in the copied town NBT (ADR-0009 whole-blob copy). So moving the flag by a delta
 * shifts every beat target by exactly that delta, and the carried rotation keeps the arc's
 * orientation intact. These tests prove that re-derivation is position-relative — the mid-move
 * "does it crash / do beats re-anchor" question the {@code flag/relocate_nearby} autotest
 * exercises end-to-end, this pins deterministically.
 */
class HelperChickenBeatRelocationTest {

    private static final BlockPos OLD_FLAG = new BlockPos(100, 64, 200);
    private static final BlockPos NEW_FLAG = new BlockPos(-40, 70, 15); // arbitrary move, incl. Y change

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
        // The four non-positional beats stay null wherever the flag sits — relocation can't
        // conjure a phantom peck target for them.
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
        // A rotated offset lands somewhere different than the un-rotated one. This is why the
        // structure rotation is carried verbatim in the relocation copy: re-deriving with a
        // wrong (e.g. default) rotation would put the chicken on the wrong side of the new flag.
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
        // WAITING_FOR_STICK targets the flag itself (FLAG_OFFSET = 0,0,0), so its target is the
        // flag position under every rotation — the cleanest anchor that the target tracks the flag.
        for (Rotation rotation : Rotation.values()) {
            Assertions.assertEquals(
                    NEW_FLAG,
                    HelperChickenBeatOffsets.resolveTarget(ChickenBeatState.WAITING_FOR_STICK, NEW_FLAG, rotation),
                    "the stick beat pecks the flag itself, wherever the flag now sits"
            );
        }
    }
}
