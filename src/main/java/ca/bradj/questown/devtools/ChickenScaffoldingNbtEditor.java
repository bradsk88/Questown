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
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the helper chicken's scaffolding (an unlit campfire, a half-built room, two gate posts)
 * into {@code empty_town.nbt}, from {@link ChickenScaffoldingLayout}. Running it twice gives the
 * same structure.
 *
 * <p>Run it with {@code ./gradlew editEmptyTownNbt} (or {@code --check} to report drift only).
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

    /** @return 0 when the file matches the layout, 1 on drift in {@code CHECK} mode, other non-zero on error */
    public static int run(Path path, Mode mode, boolean writeBak) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("structure file not found: " + path.toAbsolutePath());
        }
        CompoundTag tag;
        try (var in = Files.newInputStream(path)) {
            tag = NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
        }
        CompoundTag updated = applyScaffolding(tag);

        if (mode == Mode.CHECK) {
            // compareNbt only checks a subset, so compare the palette, blocks and size directly.
            ListTag inputBlocks = tag.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);
            ListTag updatedBlocks = updated.getList(BLOCKS_KEY, Tag.TAG_COMPOUND);
            boolean matches = inputBlocks.size() == updatedBlocks.size()
                    && NbtUtils.compareNbt(inputBlocks, updatedBlocks, true)
                    && NbtUtils.compareNbt(updatedBlocks, inputBlocks, true);
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

    /** The structure with the scaffolding added. The input is not changed. */
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

        // The layout is relative to the flag; the structure is relative to its corner.
        BlockPos flagAnchor = findFlagAnchor(palette, blocks);
        if (flagAnchor == null) {
            throw new IllegalStateException(
                    "empty_town.nbt has no questown:*_flag_base block — cannot anchor chicken scaffolding"
            );
        }

        List<ChickenScaffoldingLayout.BlockPlacement> plan =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);

        // Remove old campfires and gate posts first, so a moved offset leaves no duplicates.
        removeAllBlocksOfType(blocks, palette, "minecraft:campfire");

        for (ChickenScaffoldingLayout.BlockPlacement placement : plan) {
            BlockPos structurePos = flagAnchor.offset(placement.offset());
            removeBlocksAt(blocks, structurePos);
            int paletteIdx = ensurePaletteEntry(palette, placement.blockState());
            blocks.add(newBlockEntry(paletteIdx, structurePos));
        }

        // Clear the layout's gaps (door, wall hole), so a moved door leaves no wall behind.
        for (BlockPos gap : ChickenScaffoldingLayout.gapOffsets(Rotation.NONE)) {
            removeBlocksAt(blocks, flagAnchor.offset(gap));
        }

        expandSizeIfNeeded(root, plan, flagAnchor);
        return root;
    }

    /** The flag's position in the structure, or null if it has none. */
    private static BlockPos findFlagAnchor(ListTag palette, ListTag blocks) {
        List<Integer> flagPaletteIndices = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            String name = entry.getString("Name");
            if (name.endsWith("_flag_base") && name.startsWith("questown:")) {
                flagPaletteIndices.add(i);
            }
        }
        if (flagPaletteIndices.isEmpty()) {
            return null;
        }
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            int stateIdx = entry.getInt(STATE_KEY);
            if (!flagPaletteIndices.contains(stateIdx)) {
                continue;
            }
            BlockPos pos = readBlockPos(entry);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    private static void removeAllBlocksOfType(ListTag blocks, ListTag palette, String blockName) {
        java.util.Set<Integer> matchingPaletteIdx = new java.util.HashSet<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (blockName.equals(entry.getString("Name"))) {
                matchingPaletteIdx.add(i);
            }
        }
        if (matchingPaletteIdx.isEmpty()) {
            return;
        }
        for (int i = blocks.size() - 1; i >= 0; i--) {
            CompoundTag entry = blocks.getCompound(i);
            if (matchingPaletteIdx.contains(entry.getInt(STATE_KEY))) {
                blocks.remove(i);
            }
        }
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
            List<ChickenScaffoldingLayout.BlockPlacement> plan,
            BlockPos flagAnchor
    ) {
        int maxX = 0, maxY = 0, maxZ = 0;
        for (ChickenScaffoldingLayout.BlockPlacement p : plan) {
            BlockPos abs = flagAnchor.offset(p.offset());
            maxX = Math.max(maxX, abs.getX() + 1);
            maxY = Math.max(maxY, abs.getY() + 1);
            maxZ = Math.max(maxZ, abs.getZ() + 1);
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
