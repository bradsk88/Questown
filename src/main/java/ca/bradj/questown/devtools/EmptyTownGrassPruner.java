package ca.bradj.questown.devtools;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/**
 * Removes grass from {@code empty_town.nbt} except within a block of the flag and of the first
 * room. Run the disabled test entry point, commit the NBT, and disable it again.
 */
public final class EmptyTownGrassPruner {

    public static final Path DEFAULT_STRUCTURE_PATH = Path.of("src/main/resources/data/questown/structures/empty_town.nbt");

    private static final String PALETTE_KEY = "palette";
    private static final String BLOCKS_KEY = "blocks";
    private static final String STATE_KEY = "state";
    private static final String POS_KEY = "pos";
    private static final String NAME_KEY = "Name";

    private static final String GRASS = "minecraft:grass";
    private static final String TALL_GRASS = "minecraft:tall_grass";

    private EmptyTownGrassPruner() {
    }

    public static int run(Path path, boolean writeBak) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("structure file not found: " + path.toAbsolutePath());
        }
        CompoundTag tag;
        try (var in = Files.newInputStream(path)) {
            tag = NbtIo.readCompressed(in);
        }
        CompoundTag updated = pruneDistantGrass(tag);

        if (writeBak) {
            Path backup = path.resolveSibling(path.getFileName().toString() + ".prunebak");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try (var out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(updated, out);
        }
        System.out.println("[EmptyTownGrassPruner] applied to " + path);
        return 0;
    }

    /** The structure without grass outside the keep zone. The input is not changed. */
    public static CompoundTag pruneDistantGrass(CompoundTag source) {
        CompoundTag root = source.copy();
        ListTag palette = root.getList(PALETTE_KEY, Tag.TAG_COMPOUND);
        ListTag blocks = root.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);

        Set<Integer> grassIdxs = paletteIndicesNamed(palette, GRASS, TALL_GRASS);
        if (grassIdxs.isEmpty()) {
            return root;
        }
        BlockPos flagAnchor = findFlagAnchor(palette, blocks);
        if (flagAnchor == null) {
            throw new IllegalStateException(
                    "structure has no questown:*_flag_base — cannot anchor grass keep zone"
            );
        }
        for (int i = blocks.size() - 1; i >= 0; i--) {
            CompoundTag entry = blocks.getCompound(i);
            if (!grassIdxs.contains(entry.getInt(STATE_KEY))) {
                continue;
            }
            BlockPos pos = readBlockPos(entry);
            if (pos == null) {
                continue;
            }
            int dx = pos.getX() - flagAnchor.getX();
            int dz = pos.getZ() - flagAnchor.getZ();
            if (!isInKeepZone(dx, dz)) {
                blocks.remove(i);
            }
        }
        return root;
    }

    /** Whether a flag-relative cell is within a block of the flag or the room. */
    static boolean isInKeepZone(int dx, int dz) {
        boolean nearFlag = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
        boolean nearRoom = dx >= 1 && dx <= 7 && dz >= 1 && dz <= 7;
        return nearFlag || nearRoom;
    }

    private static Set<Integer> paletteIndicesNamed(ListTag palette, String... names) {
        Set<String> wanted = Set.of(names);
        Set<Integer> matches = new HashSet<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (wanted.contains(entry.getString(NAME_KEY))) {
                matches.add(i);
            }
        }
        return matches;
    }

    private static BlockPos findFlagAnchor(ListTag palette, ListTag blocks) {
        Set<Integer> flagIdxs = new HashSet<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String name = entry.getString(NAME_KEY);
            if (name.startsWith("questown:") && name.endsWith("_flag_base")) {
                flagIdxs.add(i);
            }
        }
        if (flagIdxs.isEmpty()) {
            return null;
        }
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            if (!flagIdxs.contains(entry.getInt(STATE_KEY))) {
                continue;
            }
            BlockPos pos = readBlockPos(entry);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos readBlockPos(CompoundTag blockEntry) {
        if (blockEntry.contains(POS_KEY, Tag.TAG_INT_ARRAY)) {
            int[] arr = blockEntry.getIntArray(POS_KEY);
            if (arr.length != 3) {
                return null;
            }
            return new BlockPos(arr[0], arr[1], arr[2]);
        }
        if (!blockEntry.contains(POS_KEY, Tag.TAG_LIST)) {
            return null;
        }
        ListTag list = blockEntry.getList(POS_KEY, Tag.TAG_INT);
        if (list.size() != 3) {
            return null;
        }
        return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
    }
}
