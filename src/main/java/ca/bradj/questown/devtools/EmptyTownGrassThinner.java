package ca.bradj.questown.devtools;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * One-shot tool that thins the long-grass cover in {@code empty_town.nbt}.
 * Replaces every {@code minecraft:tall_grass} (2-block plant) with the 1-block
 * {@code minecraft:grass}, then deletes ~50% of the resulting grass blocks
 * via an independent fixed-seed coin flip per block, so the removal is
 * spatially even and the run is reproducible.
 *
 * <p>Sibling to {@link ChickenScaffoldingNbtEditor} — same invocation pattern
 * (run the disabled JUnit entry point in this class's test to mutate the real
 * resource, restore {@code @Disabled} after).
 */
public final class EmptyTownGrassThinner {

    public static final Path DEFAULT_STRUCTURE_PATH = ChickenScaffoldingNbtEditor.DEFAULT_STRUCTURE_PATH;

    private static final String PALETTE_KEY = "palette";
    private static final String BLOCKS_KEY = "blocks";
    private static final String STATE_KEY = "state";
    private static final String NAME_KEY = "Name";
    private static final String PROPERTIES_KEY = "Properties";
    private static final String HALF_KEY = "half";

    private static final String TALL_GRASS = "minecraft:tall_grass";
    private static final String GRASS = "minecraft:grass";

    /** Fraction of grass blocks (after lowering tall_grass) kept. 0.5 = remove ~50%. */
    static final double KEEP_FRACTION = 0.5;
    /** Stable seed so re-runs are reproducible. */
    static final long REMOVAL_SEED = 0xC1CEBABEL;

    private EmptyTownGrassThinner() {
    }

    public enum Mode { LOWER_ONLY, LOWER_AND_THIN }

    public static int run(Path path, Mode mode, boolean writeBak) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("structure file not found: " + path.toAbsolutePath());
        }
        CompoundTag tag;
        try (var in = Files.newInputStream(path)) {
            tag = NbtIo.readCompressed(in);
        }
        CompoundTag updated = mode == Mode.LOWER_AND_THIN ? thinGrass(tag) : lowerTallGrass(tag);

        if (writeBak) {
            Path backup = path.resolveSibling(path.getFileName().toString() + ".grassbak");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try (var out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(updated, out);
        }
        System.out.println("[EmptyTownGrassThinner] " + mode + " applied to " + path);
        return 0;
    }

    /**
     * Pure mutation: replaces every {@code minecraft:tall_grass} block with a
     * 1-block {@code minecraft:grass}; upper halves are removed. Does NOT
     * randomly drop blocks. Input tag is not mutated.
     */
    public static CompoundTag lowerTallGrass(CompoundTag source) {
        CompoundTag root = source.copy();
        ListTag palette = root.getList(PALETTE_KEY, Tag.TAG_COMPOUND);
        ListTag blocks = root.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);

        Map<Integer, String> tallGrassHalfByIdx = tallGrassHalvesByPaletteIdx(palette);
        int grassPaletteIdx = ensureGrassPaletteEntry(palette);
        rewriteTallGrass(blocks, tallGrassHalfByIdx, grassPaletteIdx);
        return root;
    }

    /**
     * Pure mutation: lowers tall_grass→grass and randomly drops ~50% of the
     * remaining grass blocks. Input tag is not mutated.
     */
    public static CompoundTag thinGrass(CompoundTag source) {
        CompoundTag root = lowerTallGrass(source);
        ListTag palette = root.getList(PALETTE_KEY, Tag.TAG_COMPOUND);
        ListTag blocks = root.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);
        thinGrassBlocks(blocks, paletteIndicesNamed(palette, GRASS));
        return root;
    }

    /**
     * Maps each tall_grass palette index to its {@code half} property
     * ({@code "lower"} / {@code "upper"}, defaulting to {@code "lower"} when
     * absent — a defensive fallback for hand-edited palettes).
     */
    private static Map<Integer, String> tallGrassHalvesByPaletteIdx(ListTag palette) {
        Map<Integer, String> out = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (!TALL_GRASS.equals(entry.getString(NAME_KEY))) {
                continue;
            }
            String half = "lower";
            if (entry.contains(PROPERTIES_KEY, Tag.TAG_COMPOUND)) {
                CompoundTag props = entry.getCompound(PROPERTIES_KEY);
                if (props.contains(HALF_KEY, Tag.TAG_STRING)) {
                    half = props.getString(HALF_KEY);
                }
            }
            out.put(i, half);
        }
        return out;
    }

    private static Set<Integer> paletteIndicesNamed(ListTag palette, String blockName) {
        Set<Integer> matches = new HashSet<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (blockName.equals(entry.getString(NAME_KEY))) {
                matches.add(i);
            }
        }
        return matches;
    }

    private static int ensureGrassPaletteEntry(ListTag palette) {
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (GRASS.equals(entry.getString(NAME_KEY)) && !entry.contains(PROPERTIES_KEY)) {
                return i;
            }
        }
        CompoundTag entry = NbtUtils.writeBlockState(Blocks.GRASS.defaultBlockState());
        palette.add(entry);
        return palette.size() - 1;
    }

    /**
     * For every block referencing a tall_grass palette entry: lower halves
     * become 1-block {@code minecraft:grass}; upper halves are removed.
     */
    private static void rewriteTallGrass(
            ListTag blocks,
            Map<Integer, String> tallGrassHalfByIdx,
            int grassPaletteIdx
    ) {
        if (tallGrassHalfByIdx.isEmpty()) {
            return;
        }
        for (int i = blocks.size() - 1; i >= 0; i--) {
            CompoundTag entry = blocks.getCompound(i);
            String half = tallGrassHalfByIdx.get(entry.getInt(STATE_KEY));
            if (half == null) {
                continue;
            }
            if ("upper".equals(half)) {
                blocks.remove(i);
                continue;
            }
            entry.putInt(STATE_KEY, grassPaletteIdx);
        }
    }

    /**
     * Independent fixed-seed Bernoulli(KEEP_FRACTION) per grass block — yields
     * spatially even thinning, not a contiguous block of removals.
     */
    private static void thinGrassBlocks(ListTag blocks, Set<Integer> grassPaletteIdxs) {
        Random rng = new Random(REMOVAL_SEED);
        for (int i = blocks.size() - 1; i >= 0; i--) {
            CompoundTag entry = blocks.getCompound(i);
            if (!grassPaletteIdxs.contains(entry.getInt(STATE_KEY))) {
                continue;
            }
            if (rng.nextDouble() >= KEEP_FRACTION) {
                blocks.remove(i);
            }
        }
    }
}
