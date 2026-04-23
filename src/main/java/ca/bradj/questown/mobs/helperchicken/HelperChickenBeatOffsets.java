package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

/**
 * Structure-local {@link BlockPos} offsets for each beat target in the helper-chicken
 * onboarding arc (U3). The values here are placeholders — U5 authors the real offsets
 * when editing {@code empty_town.nbt}.
 *
 * <p>Offsets are stored in the structure's <em>local</em> frame (pre-rotation). At
 * runtime, {@link #resolveTarget(ChickenBeatState, BlockPos, Rotation)} applies the
 * flag BE's persisted rotation via {@link BlockPos#rotate(Rotation)} before adding
 * the flag's world position.
 *
 * <p>{@code CAMPFIRE_OFFSET} doubles as the rotation-detection anchor for
 * {@link ca.bradj.questown.town.HelperChickenRotationDetector}. It must be authored
 * at an asymmetric offset so the 4 rotated candidate positions are all distinct.
 *
 * <p>Mirror support is out of scope for v1.
 */
public final class HelperChickenBeatOffsets {

    // Provisional offsets (U5). Values here encode the structural shape the
    // helper chicken arc targets; the authored empty_town.nbt edit must place
    // the matching blocks at these coordinates in the structure-local frame.
    // See docs/conventions/editing-empty-town-nbt.md for the round-trip process.
    //
    // CAMPFIRE_OFFSET doubles as the rotation-detection anchor. The chosen
    // value is asymmetric under all four rotations so scanning the 4 rotated
    // candidate positions disambiguates cleanly.

    public static final BlockPos CAMPFIRE_OFFSET = new BlockPos(3, 0, 5);
    public static final BlockPos WALL_BLOCK_OFFSET = new BlockPos(6, 0, 3);
    public static final BlockPos DOOR_OFFSET = new BlockPos(6, 0, 2);
    public static final BlockPos SIGN_OFFSET = new BlockPos(7, 0, 4);
    public static final BlockPos CHEST_OFFSET = new BlockPos(8, 0, 4);
    public static final BlockPos GATE_CENTER_OFFSET = new BlockPos(2, 0, 9);

    private HelperChickenBeatOffsets() {
    }

    /**
     * Resolves the world-space beat target for the chicken's current beat state.
     *
     * <p>Returns {@code null} when the beat state has no positional target — callers
     * must handle null. Non-target states: {@link ChickenBeatState#COMPLETE},
     * {@link ChickenBeatState#FORFEIT}, {@link ChickenBeatState#SUNSET_AND_MAP},
     * {@link ChickenBeatState#AWAITING_WORLDLY_SEEDS_DELIVERY}.
     */
    @Nullable
    public static BlockPos resolveTarget(
            ChickenBeatState state,
            BlockPos flagPos,
            Rotation rotation
    ) {
        BlockPos localOffset = localOffsetForState(state);
        if (localOffset == null) {
            return null;
        }
        return flagPos.offset(localOffset.rotate(rotation));
    }

    @Nullable
    private static BlockPos localOffsetForState(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK, WAITING_FOR_WAND_ON_CAMPFIRE -> CAMPFIRE_OFFSET;
            case WAITING_FOR_WALL_BLOCK -> WALL_BLOCK_OFFSET;
            case WAITING_FOR_DOOR, WAITING_FOR_WAND_ON_DOOR -> DOOR_OFFSET;
            case WAITING_FOR_SIGN -> SIGN_OFFSET;
            case WAITING_FOR_CHEST -> CHEST_OFFSET;
            case WAITING_FOR_PRESSURE_PLATE -> GATE_CENTER_OFFSET;
            case WAITING_FOR_VILLAGER_UI, WAITING_FOR_FLAG_UI -> CAMPFIRE_OFFSET;
            case SUNSET_AND_MAP, AWAITING_WORLDLY_SEEDS_DELIVERY, COMPLETE, FORFEIT -> null;
        };
    }
}
