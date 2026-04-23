package ca.bradj.questown.devtools;

import ca.bradj.questown.mobs.helperchicken.ChickenScaffoldingLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * One-shot tool that writes the helper-chicken scaffolding blocks into
 * {@code empty_town.nbt} so players see an unlit campfire, a mostly-built
 * cobblestone room, and two fence gate columns around every newly-generated
 * town flag.
 *
 * <p>Reads {@link ChickenScaffoldingLayout} for the layout itself — that class
 * is also consumed by the in-world {@code ChickenArcTestExecutor}, so the
 * committed structure and the headless test arena stay tag-equivalent.
 *
 * <p>Idempotent by design. Running the editor twice on the same input produces
 * the same tag tree (verified by {@code NbtUtils.compareNbt(a, b, true)};
 * byte-level equality is not guaranteed because gzip output varies across JVMs).
 * Existing blocks at target offsets are removed before inserts, and the palette
 * is only appended to, never rewritten.
 *
 * <h2>Invocation</h2>
 * <p>Run via the Gradle convenience task, which delegates to the JUnit
 * {@code applyToRealStructure} test so the editor inherits the
 * {@code Bootstrap.bootStrap()} classpath it needs:
 *
 * <pre>{@code
 *   ./gradlew editEmptyTownNbt         # writes the file + sibling .bak
 *   ./gradlew editEmptyTownNbt --check # reports diff only, exits non-zero if drift
 * }</pre>
 *
 * <p>{@code main(String[])} also works as a fallback for a manual
 * {@code java -cp ... ChickenScaffoldingNbtEditor} invocation, but the JUnit
 * path is the supported entry point.
 */
public final class ChickenScaffoldingNbtEditor {

    public static final Path DEFAULT_STRUCTURE_PATH = Path.of(
            "src/main/resources/data/questown/structures/empty_town.nbt"
    );

    private static final String PALETTE_KEY = "palette";
    private static final String BLOCKS_KEY = "blocks";
    private static final String SIZE_KEY = "size";
    private static final String POS_KEY = "pos";
    private static final String STATE_KEY = "state";

    private ChickenScaffoldingNbtEditor() {
    }

    public enum Mode { APPLY, CHECK }

    public static void main(String[] args) throws IOException {
        Mode mode = Mode.APPLY;
        for (String arg : args) {
            if ("--check".equals(arg)) {
                mode = Mode.CHECK;
            } else if ("--apply".equals(arg)) {
                mode = Mode.APPLY;
            }
        }
        int exit = run(DEFAULT_STRUCTURE_PATH, mode, true);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    /**
     * Core entry point used by both {@link #main} and the JUnit harness.
     *
     * @param path       the {@code .nbt} file to read (and write, unless {@code CHECK})
     * @param mode       {@link Mode#APPLY} writes the file (with a sibling {@code .bak}
     *                   first); {@link Mode#CHECK} only reports whether the file is up
     *                   to date, without writing
     * @param writeBak   when {@code true} in {@code APPLY} mode, copies the original
     *                   file to {@code <path>.bak} before writing
     * @return 0 if the file is / becomes tag-equivalent to the target layout; 1 on
     *         drift in {@code CHECK} mode; other non-zero on error
     */
    public static int run(Path path, Mode mode, boolean writeBak) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("structure file not found: " + path.toAbsolutePath());
        }
        CompoundTag tag;
        try (var in = Files.newInputStream(path)) {
            tag = NbtIo.readCompressed(in);
        }
        CompoundTag updated = applyScaffolding(tag);

        if (mode == Mode.CHECK) {
            boolean matches = NbtUtils.compareNbt(tag, updated, true);
            if (matches) {
                System.out.println("[ChickenScaffoldingNbtEditor] up to date: " + path);
                return 0;
            }
            System.out.println("[ChickenScaffoldingNbtEditor] drift detected: " + path
                    + " is missing at least one authored scaffolding block.");
            return 1;
        }

        if (writeBak) {
            Path backup = path.resolveSibling(path.getFileName().toString() + ".bak");
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try (var out = Files.newOutputStream(path)) {
            NbtIo.writeCompressed(updated, out);
        }
        System.out.println("[ChickenScaffoldingNbtEditor] wrote scaffolding to " + path);
        return 0;
    }

    /**
     * Pure-function mutation of the root structure tag. Returns a new
     * {@link CompoundTag} with the scaffolding blocks merged in. The input tag is
     * not mutated.
     *
     * <p>Exposed for testing so synthetic inputs can be fed without disk I/O.
     */
    public static CompoundTag applyScaffolding(CompoundTag source) {
        CompoundTag root = source.copy();
        ListTag palette = root.getList(PALETTE_KEY, Tag.TAG_COMPOUND);
        if (palette.isEmpty() && !root.contains(PALETTE_KEY, Tag.TAG_LIST)) {
            root.put(PALETTE_KEY, palette);
        }
        ListTag blocks = root.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);
        if (blocks.isEmpty() && !root.contains(BLOCKS_KEY, Tag.TAG_LIST)) {
            root.put(BLOCKS_KEY, blocks);
        }

        List<ChickenScaffoldingLayout.BlockPlacement> plan =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);

        for (ChickenScaffoldingLayout.BlockPlacement placement : plan) {
            removeBlocksAt(blocks, placement.offset());
            int paletteIdx = ensurePaletteEntry(palette, placement.blockState());
            blocks.add(newBlockEntry(paletteIdx, placement.offset()));
        }

        expandSizeIfNeeded(root, plan);
        return root;
    }

    private static void removeBlocksAt(ListTag blocks, BlockPos target) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Tag entry = blocks.get(i);
            if (!(entry instanceof CompoundTag c)) {
                continue;
            }
            BlockPos pos = readBlockPos(c);
            if (pos != null && pos.equals(target)) {
                blocks.remove(i);
            }
        }
    }

    private static int ensurePaletteEntry(ListTag palette, BlockState state) {
        CompoundTag target = NbtUtils.writeBlockState(state);
        for (int i = 0; i < palette.size(); i++) {
            Tag entry = palette.get(i);
            if (entry instanceof CompoundTag existing && NbtUtils.compareNbt(existing, target, true)) {
                return i;
            }
        }
        palette.add(target);
        return palette.size() - 1;
    }

    private static CompoundTag newBlockEntry(int paletteIdx, BlockPos pos) {
        CompoundTag entry = new CompoundTag();
        entry.putInt(STATE_KEY, paletteIdx);
        ListTag posList = new ListTag();
        posList.add(IntTag.valueOf(pos.getX()));
        posList.add(IntTag.valueOf(pos.getY()));
        posList.add(IntTag.valueOf(pos.getZ()));
        entry.put(POS_KEY, posList);
        return entry;
    }

    private static BlockPos readBlockPos(CompoundTag blockEntry) {
        if (!blockEntry.contains(POS_KEY, Tag.TAG_LIST)
                && !blockEntry.contains(POS_KEY, Tag.TAG_INT_ARRAY)) {
            return null;
        }
        if (blockEntry.contains(POS_KEY, Tag.TAG_INT_ARRAY)) {
            int[] arr = blockEntry.getIntArray(POS_KEY);
            if (arr.length != 3) {
                return null;
            }
            return new BlockPos(arr[0], arr[1], arr[2]);
        }
        ListTag list = blockEntry.getList(POS_KEY, Tag.TAG_INT);
        if (list.size() != 3) {
            return null;
        }
        return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
    }

    private static void expandSizeIfNeeded(
            CompoundTag root,
            List<ChickenScaffoldingLayout.BlockPlacement> plan
    ) {
        int maxX = 0, maxY = 0, maxZ = 0;
        for (ChickenScaffoldingLayout.BlockPlacement p : plan) {
            maxX = Math.max(maxX, p.offset().getX() + 1);
            maxY = Math.max(maxY, p.offset().getY() + 1);
            maxZ = Math.max(maxZ, p.offset().getZ() + 1);
        }

        ListTag size = root.getList(SIZE_KEY, Tag.TAG_INT);
        if (size.size() != 3) {
            ListTag rebuilt = new ListTag();
            rebuilt.add(IntTag.valueOf(Math.max(maxX, 1)));
            rebuilt.add(IntTag.valueOf(Math.max(maxY, 1)));
            rebuilt.add(IntTag.valueOf(Math.max(maxZ, 1)));
            root.put(SIZE_KEY, rebuilt);
            return;
        }
        int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
        int nx = Math.max(sx, maxX), ny = Math.max(sy, maxY), nz = Math.max(sz, maxZ);
        if (nx != sx || ny != sy || nz != sz) {
            ListTag rebuilt = new ListTag();
            rebuilt.add(IntTag.valueOf(nx));
            rebuilt.add(IntTag.valueOf(ny));
            rebuilt.add(IntTag.valueOf(nz));
            root.put(SIZE_KEY, rebuilt);
        }
    }
}
