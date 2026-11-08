package ca.bradj.questown.devtools;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reshapes {@code empty_town.nbt} to sit better in terrain: air above it so trees can't grow in,
 * a dirt floor under it, and grass slopes stepping down around it. The structure moves up by
 * {@link #FLOOR_DEPTH}, so {@code worldgen/structure/empty_town.json}'s {@code start_height} has
 * to come down by the same amount.
 */
public final class EmptyTownTerrainShaper {

    public static final Path DEFAULT_STRUCTURE_PATH = Path.of("src/main/resources/data/questown/structures/empty_town.nbt");

    public static final int CEILING_HEIGHT = 16;
    public static final int FLOOR_DEPTH = 3;
    public static final int CASCADE_DISTANCE = 3;

    private static final String PALETTE_KEY = "palette";
    private static final String BLOCKS_KEY = "blocks";
    private static final String SIZE_KEY = "size";
    private static final String POS_KEY = "pos";
    private static final String STATE_KEY = "state";
    private static final String NAME_KEY = "Name";

    private static final String AIR = "minecraft:air";
    private static final String DIRT = "minecraft:dirt";
    private static final String GRASS_BLOCK = "minecraft:grass_block";

    private EmptyTownTerrainShaper() {
    }

    public static int run(Path path, boolean writeBak) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("structure file not found: " + path.toAbsolutePath());
        }
        CompoundTag tag;
        try (var in = Files.newInputStream(path)) {
            tag = NbtIo.readCompressed(in);
        }
        CompoundTag updated = shape(tag);
        if (writeBak) {
            Path backup = path.resolveSibling(path.getFileName().toString() + ".terrainbak");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try (var out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(updated, out);
        }
        System.out.println("[EmptyTownTerrainShaper] reshaped terrain in " + path);
        return 0;
    }

    /** The reshaped structure. The input is not changed. */
    public static CompoundTag shape(CompoundTag source) {
        CompoundTag root = source.copy();
        ListTag palette = root.getList(PALETTE_KEY, Tag.TAG_COMPOUND);
        ListTag blocks = root.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);

        int[] origSize = readSize(root);
        int origX = origSize[0];
        int origY = origSize[1];
        int origZ = origSize[2];

        shiftAllBlocks(blocks, CASCADE_DISTANCE, FLOOR_DEPTH, CASCADE_DISTANCE);

        int dirtIdx = ensurePaletteEntry(palette, DIRT);
        int airIdx = ensurePaletteEntry(palette, AIR);
        int grassIdx = ensurePaletteEntry(palette, GRASS_BLOCK);

        int footprintX0 = CASCADE_DISTANCE;
        int footprintZ0 = CASCADE_DISTANCE;
        int footprintX1 = footprintX0 + origX - 1;
        int footprintZ1 = footprintZ0 + origZ - 1;
        int planeGroundY = FLOOR_DEPTH;
        int yTopAfterShift = origY - 1 + FLOOR_DEPTH;

        addFloor(blocks, dirtIdx, footprintX0, footprintZ0, footprintX1, footprintZ1, planeGroundY);
        addCeiling(blocks, airIdx, footprintX0, footprintZ0, footprintX1, footprintZ1, yTopAfterShift);
        addCascade(blocks, dirtIdx, grassIdx, footprintX0, footprintZ0, footprintX1, footprintZ1, planeGroundY);

        int newSizeX = origX + 2 * CASCADE_DISTANCE;
        int newSizeY = yTopAfterShift + CEILING_HEIGHT + 1;
        int newSizeZ = origZ + 2 * CASCADE_DISTANCE;
        writeSize(root, newSizeX, newSizeY, newSizeZ);

        return root;
    }

    private static void addFloor(
            ListTag blocks, int dirtIdx,
            int x0, int z0, int x1, int z1,
            int planeGroundY
    ) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y < planeGroundY; y++) {
                    blocks.add(blockEntry(dirtIdx, x, y, z));
                }
            }
        }
    }

    private static void addCeiling(
            ListTag blocks, int airIdx,
            int x0, int z0, int x1, int z1,
            int yTopAfterShift
    ) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int dy = 1; dy <= CEILING_HEIGHT; dy++) {
                    blocks.add(blockEntry(airIdx, x, yTopAfterShift + dy, z));
                }
            }
        }
    }

    /** Ring r drops r blocks below the floor, one per ring outward. */
    private static void addCascade(
            ListTag blocks, int dirtIdx, int grassIdx,
            int x0, int z0, int x1, int z1,
            int planeGroundY
    ) {
        for (int r = 1; r <= CASCADE_DISTANCE; r++) {
            int topY = planeGroundY - r;
            if (topY < 0) {
                continue;
            }
            for (BlockPos cell : ringCells(x0 - r, z0 - r, x1 + r, z1 + r,
                                           x0 - (r - 1), z0 - (r - 1),
                                           x1 + (r - 1), z1 + (r - 1))) {
                int cx = cell.getX();
                int cz = cell.getZ();
                for (int y = 0; y < topY; y++) {
                    blocks.add(blockEntry(dirtIdx, cx, y, cz));
                }
                blocks.add(blockEntry(grassIdx, cx, topY, cz));
            }
        }
    }

    /** Cells inside the outer rectangle and outside the inner one. */
    private static List<BlockPos> ringCells(
            int ox0, int oz0, int ox1, int oz1,
            int ix0, int iz0, int ix1, int iz1
    ) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = ox0; x <= ox1; x++) {
            for (int z = oz0; z <= oz1; z++) {
                if (x >= ix0 && x <= ix1 && z >= iz0 && z <= iz1) {
                    continue;
                }
                out.add(new BlockPos(x, 0, z));
            }
        }
        return out;
    }

    private static int[] readSize(CompoundTag root) {
        ListTag size = root.getList(SIZE_KEY, Tag.TAG_INT);
        if (size.size() != 3) {
            throw new IllegalStateException("structure root tag missing valid size: " + size);
        }
        return new int[]{size.getInt(0), size.getInt(1), size.getInt(2)};
    }

    private static void writeSize(CompoundTag root, int sx, int sy, int sz) {
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(sx));
        size.add(IntTag.valueOf(sy));
        size.add(IntTag.valueOf(sz));
        root.put(SIZE_KEY, size);
    }

    private static void shiftAllBlocks(ListTag blocks, int dx, int dy, int dz) {
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            ListTag pos = entry.getList(POS_KEY, Tag.TAG_INT);
            if (pos.size() != 3) {
                continue;
            }
            ListTag rebuilt = new ListTag();
            rebuilt.add(IntTag.valueOf(pos.getInt(0) + dx));
            rebuilt.add(IntTag.valueOf(pos.getInt(1) + dy));
            rebuilt.add(IntTag.valueOf(pos.getInt(2) + dz));
            entry.put(POS_KEY, rebuilt);
        }
    }

    private static int ensurePaletteEntry(ListTag palette, String blockName) {
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (blockName.equals(entry.getString(NAME_KEY)) && !entry.contains("Properties")) {
                return i;
            }
        }
        CompoundTag entry = NbtUtils.writeBlockState(switch (blockName) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case GRASS_BLOCK -> Blocks.GRASS_BLOCK.defaultBlockState();
            default -> throw new IllegalArgumentException("unknown block: " + blockName);
        });
        // Write the plain default grass block.
        entry.remove("Properties");
        palette.add(entry);
        return palette.size() - 1;
    }

    private static CompoundTag blockEntry(int paletteIdx, int x, int y, int z) {
        CompoundTag entry = new CompoundTag();
        entry.putInt(STATE_KEY, paletteIdx);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        entry.put(POS_KEY, pos);
        return entry;
    }
}
