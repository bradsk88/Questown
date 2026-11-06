package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

/** Where each helper-chicken lesson happens, relative to the flag in {@code empty_town.nbt}, before rotation. The campfire offset also detects the rotation, so it is asymmetric. */
public final class HelperChickenBeatOffsets {

    // West of the flag, outside the room. Asymmetric under all four rotations.
    public static final BlockPos CAMPFIRE_OFFSET = new BlockPos(-2, 0, 1);

    /** The flag itself: the stick lesson pecks the flag. */
    public static final BlockPos FLAG_OFFSET = new BlockPos(0, 0, 0);
    public static final BlockPos WALL_BLOCK_OFFSET = new BlockPos(6, 0, 3);
    // The middle of the +x wall; a door can't go in a corner.
    public static final BlockPos DOOR_OFFSET = new BlockPos(6, 0, 4);
    // Inside the room, opposite the chest.
    public static final BlockPos SIGN_OFFSET = new BlockPos(3, 0, 4);
    public static final BlockPos CHEST_OFFSET = new BlockPos(5, 0, 4);
    public static final BlockPos GATE_CENTER_OFFSET = new BlockPos(2, 0, 9);

    private HelperChickenBeatOffsets() {
    }

    /** Where the chicken looks for the current lesson, or null for lessons with no place. */
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

    /** Where the chicken stands for the current lesson: next to the campfire rather than in it. */
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
            case WAITING_FOR_DOOR, WAITING_FOR_WAND_ON_DOOR, WAITING_FOR_ROOM_BUILD -> DOOR_OFFSET;
            case WAITING_FOR_SIGN -> SIGN_OFFSET;
            case WAITING_FOR_CHEST -> CHEST_OFFSET;
            case WAITING_FOR_PRESSURE_PLATE -> GATE_CENTER_OFFSET;
            case WAITING_FOR_FREE_FOOD, WAITING_FOR_VILLAGER_UI, WAITING_FOR_FLAG_UI -> CAMPFIRE_OFFSET;
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
            // South of the campfire, outside the room and the gate.
            case WAITING_FOR_WAND_ON_CAMPFIRE,
                 WAITING_FOR_VILLAGER_UI,
                 WAITING_FOR_FLAG_UI -> focal.south();
            default -> focal;
        };
    }
}
