package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Source of truth for the helper-chicken arc's authored scaffolding layout.
 *
 * <p>Consumed by both the one-shot {@code ChickenScaffoldingNbtEditor} (which writes
 * these blocks into {@code empty_town.nbt}) and the in-world
 * {@code ChickenArcTestExecutor} PLACE_SCAFFOLDING phase. Keeping one source for both
 * call sites guarantees the committed structure and the headless test arena are
 * tag-equivalent.
 *
 * <p>The base layout is authored for {@link Rotation#NONE}. Other rotations are
 * derived by rotating the offset via {@link BlockPos#rotate(Rotation)} and rotating
 * any directional block properties.
 *
 * <p>Layout contract:
 * <ul>
 *   <li>One unlit campfire at {@link HelperChickenBeatOffsets#CAMPFIRE_OFFSET}.</li>
 *   <li>Cobblestone room perimeter around a 5&times;5 footprint
 *       (x &isin; [2..6], z &isin; [2..6], y = 0), minus:
 *       <ul>
 *           <li>{@link HelperChickenBeatOffsets#WALL_BLOCK_OFFSET} (the "missing wall"
 *               gap the player completes during F3).</li>
 *           <li>{@link HelperChickenBeatOffsets#DOOR_OFFSET} (the door gap the player
 *               fills with an oak door during F3).</li>
 *       </ul>
 *   </li>
 *   <li>Two oak fence posts flanking {@link HelperChickenBeatOffsets#GATE_CENTER_OFFSET}
 *       at {@code (1, 0, 9)} and {@code (3, 0, 9)}, leaving the center position open as
 *       the gate passage.</li>
 * </ul>
 */
public final class ChickenScaffoldingLayout {

    private ChickenScaffoldingLayout() {
    }

    /**
     * Returns the scaffolding plan in the given rotation, as a list of
     * {@link BlockPlacement} entries. Positions are relative to the flag origin.
     *
     * <p>Calling with {@link Rotation#NONE} returns the authored base layout;
     * other rotations return the same block set with positions and directional
     * block properties rotated accordingly.
     */
    public static List<BlockPlacement> forRotation(Rotation rotation) {
        List<BlockPlacement> base = baseLayout();
        if (rotation == Rotation.NONE) {
            return Collections.unmodifiableList(base);
        }
        List<BlockPlacement> rotated = new ArrayList<>(base.size());
        for (BlockPlacement placement : base) {
            rotated.add(new BlockPlacement(
                    placement.offset().rotate(rotation),
                    placement.blockState().rotate(rotation)
            ));
        }
        return Collections.unmodifiableList(rotated);
    }

    private static List<BlockPlacement> baseLayout() {
        List<BlockPlacement> out = new ArrayList<>();
        addCampfire(out);
        addRoomPerimeter(out);
        addGateColumns(out);
        return out;
    }

    private static void addCampfire(List<BlockPlacement> out) {
        BlockState unlit = Blocks.CAMPFIRE.defaultBlockState()
                .setValue(CampfireBlock.LIT, false);
        out.add(new BlockPlacement(HelperChickenBeatOffsets.CAMPFIRE_OFFSET, unlit));
    }

    private static void addRoomPerimeter(List<BlockPlacement> out) {
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockPos wallGap = HelperChickenBeatOffsets.WALL_BLOCK_OFFSET;
        BlockPos doorGap = HelperChickenBeatOffsets.DOOR_OFFSET;

        // Walls are 2-high so the RoomRecipes detector can register the room
        // once the player places the last wall block and a door. Gaps:
        //   - WALL_BLOCK_OFFSET (y=0 only) — the player's "missing wall"
        //   - DOOR_OFFSET (y=0 AND y=1)   — where the 2-high oak door goes
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                boolean onPerimeter = (x == 2 || x == 6 || z == 2 || z == 6);
                if (!onPerimeter) {
                    continue;
                }
                for (int y = 0; y <= 1; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (y == 0 && pos.equals(wallGap)) {
                        continue;
                    }
                    if (pos.getX() == doorGap.getX() && pos.getZ() == doorGap.getZ()) {
                        // Both halves of the door column stay open.
                        continue;
                    }
                    out.add(new BlockPlacement(pos, cobble));
                }
            }
        }
    }

    private static void addGateColumns(List<BlockPlacement> out) {
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockPos center = HelperChickenBeatOffsets.GATE_CENTER_OFFSET;
        out.add(new BlockPlacement(new BlockPos(center.getX() - 1, center.getY(), center.getZ()), fence));
        out.add(new BlockPlacement(new BlockPos(center.getX() + 1, center.getY(), center.getZ()), fence));
    }

    /**
     * A single scaffolding block at a structure-local offset.
     *
     * <p>Field names mirror {@code TestBlueprint.BlockPlacement} for easy
     * interop at call sites that bridge the two types.
     */
    public record BlockPlacement(BlockPos offset, BlockState blockState) {
    }
}
