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
 * The helper chicken's scaffolding, for {@code empty_town.nbt} and the autotest arena alike: an unlit
 * campfire, a cobblestone room missing one wall block and its door, and two gate posts. Authored
 * for {@link Rotation#NONE}; other rotations are derived.
 */
public final class ChickenScaffoldingLayout {

    private ChickenScaffoldingLayout() {
    }

    /** The scaffolding in the given rotation, relative to the flag. */
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

    /** The cells that must stay empty: the missing wall block and the door column. */
    public static List<BlockPos> gapOffsets(Rotation rotation) {
        List<BlockPos> gaps = new ArrayList<>();
        gaps.add(HelperChickenBeatOffsets.WALL_BLOCK_OFFSET);
        BlockPos door = HelperChickenBeatOffsets.DOOR_OFFSET;
        gaps.add(door);
        gaps.add(new BlockPos(door.getX(), door.getY() + 1, door.getZ()));
        if (rotation == Rotation.NONE) {
            return Collections.unmodifiableList(gaps);
        }
        List<BlockPos> rotated = new ArrayList<>(gaps.size());
        for (BlockPos g : gaps) {
            rotated.add(g.rotate(rotation));
        }
        return Collections.unmodifiableList(rotated);
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

        // Two-high walls, leaving the missing wall block and the door column open.
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

    /** One scaffolding block, relative to the flag. */
    public record BlockPlacement(BlockPos offset, BlockState blockState) {
    }
}
