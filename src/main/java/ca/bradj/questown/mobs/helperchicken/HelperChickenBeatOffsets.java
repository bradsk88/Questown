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

    // Placed west of the flag and OUTSIDE the room footprint (room perimeter
    // occupies x∈[2..6], z∈[2..6]). Asymmetric under all four rotations so the
    // detector can disambiguate; verified non-colliding with the flag origin
    // and the gate fence columns at (1,0,9)/(3,0,9).
    public static final BlockPos CAMPFIRE_OFFSET = new BlockPos(-2, 0, 1);

    /**
     * Flag base offset — the flag block sits at the structure origin. Used as the
     * peck target for {@link ChickenBeatState#WAITING_FOR_STICK}: the player's
     * next action is right-clicking the flag with a stick to mint a town wand,
     * so the chicken stands at and pecks the flag rather than the campfire.
     */
    public static final BlockPos FLAG_OFFSET = new BlockPos(0, 0, 0);
    public static final BlockPos WALL_BLOCK_OFFSET = new BlockPos(6, 0, 3);
    // Middle of the +x wall (perimeter z spans 2..6, midpoint z=4). A
    // doorway on a corner cell (x=6,z=2) is invalid in MC — doors require
    // a single-cell opening with cobblestone neighbours on both sides.
    public static final BlockPos DOOR_OFFSET = new BlockPos(6, 0, 4);
    // Inside the room interior (perimeter walls at x=2/x=6, z=2/z=6 → interior
    // is x∈[3..5], z∈[3..5]). Sign and chest sit on opposite interior walls so
    // the room recipe scan picks them up and the job-board conversion fires.
    public static final BlockPos SIGN_OFFSET = new BlockPos(3, 0, 4);
    public static final BlockPos CHEST_OFFSET = new BlockPos(5, 0, 4);
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

    /**
     * World-space position where the chicken should STAND while pecking. Most
     * beats use the same block as the focal target ({@link #resolveTarget}),
     * but beats that focus on a non-walkable / hazardous block (campfire)
     * resolve to a cardinal-adjacent walkable cell so the chicken is not
     * rendered standing inside the fire. Look direction stays at the focal
     * target — the peck goal calls {@link #resolveTarget} for that.
     */
    @Nullable
    public static BlockPos resolveStandTarget(
            ChickenBeatState state,
            BlockPos flagPos,
            Rotation rotation
    ) {
        BlockPos localOffset = localStandOffsetForState(state);
        if (localOffset == null) {
            return null;
        }
        return flagPos.offset(localOffset.rotate(rotation));
    }

    @Nullable
    private static BlockPos localOffsetForState(ChickenBeatState state) {
        return switch (state) {
            case WAITING_FOR_STICK -> FLAG_OFFSET;
            case WAITING_FOR_WAND_ON_CAMPFIRE -> CAMPFIRE_OFFSET;
            case WAITING_FOR_WALL_BLOCK -> WALL_BLOCK_OFFSET;
            case WAITING_FOR_DOOR, WAITING_FOR_WAND_ON_DOOR -> DOOR_OFFSET;
            case WAITING_FOR_SIGN -> SIGN_OFFSET;
            case WAITING_FOR_CHEST -> CHEST_OFFSET;
            case WAITING_FOR_PRESSURE_PLATE -> GATE_CENTER_OFFSET;
            case WAITING_FOR_VILLAGER_UI, WAITING_FOR_FLAG_UI -> CAMPFIRE_OFFSET;
            case SUNSET_AND_MAP, AWAITING_WORLDLY_SEEDS_DELIVERY, COMPLETE, FORFEIT -> null;
        };
    }

    @Nullable
    private static BlockPos localStandOffsetForState(ChickenBeatState state) {
        BlockPos focal = localOffsetForState(state);
        if (focal == null) {
            return null;
        }
        return switch (state) {
            // Campfire-focused beats: stand south of the campfire (z+1) so
            // the chicken doesn't render inside the fire. Cobblestone floor
            // surrounds the campfire on every side, so any cardinal works;
            // south is consistently outside the room and the gate column.
            case WAITING_FOR_WAND_ON_CAMPFIRE,
                 WAITING_FOR_VILLAGER_UI,
                 WAITING_FOR_FLAG_UI -> focal.south();
            default -> focal;
        };
    }
}
