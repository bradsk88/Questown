package ca.bradj.questown.devtools;

import ca.bradj.questown.mobs.helperchicken.ChickenScaffoldingLayout;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ChickenScaffoldingNbtEditorTest {

    /** The flag sits off the origin, so a layout written without translating fails every check. */
    private static final BlockPos FLAG_ANCHOR = new BlockPos(5, 1, 7);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void applyScaffolding_insertsPlannedBlocksIntoEmptyStructure() {
        CompoundTag tag = freshStructureTag();
        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);

        List<ChickenScaffoldingLayout.BlockPlacement> plan =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        ListTag blocks = result.getList("blocks", Tag.TAG_COMPOUND);
        Assertions.assertEquals(plan.size() + 1, blocks.size(),
                "every planned placement lands as one block entry (plus the seed flag)");

        for (ChickenScaffoldingLayout.BlockPlacement placement : plan) {
            BlockPos expected = FLAG_ANCHOR.offset(placement.offset());
            Assertions.assertTrue(
                    containsBlockAt(blocks, expected),
                    "missing planned block at " + expected
                            + " (= flag " + FLAG_ANCHOR + " + layout " + placement.offset() + ")"
            );
        }
    }

    @Test
    void applyScaffolding_palettePointsAtRealBlockStates() {
        CompoundTag tag = freshStructureTag();
        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);

        ListTag palette = result.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = result.getList("blocks", Tag.TAG_COMPOUND);

        BlockPos campfirePos = FLAG_ANCHOR.offset(ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets.CAMPFIRE_OFFSET);
        CompoundTag campfireEntry = findBlockAt(blocks, campfirePos);
        Assertions.assertNotNull(campfireEntry,
                "campfire must be present at flag + CAMPFIRE_OFFSET = " + campfirePos);
        int stateIdx = campfireEntry.getInt("state");
        Assertions.assertTrue(
                stateIdx >= 0 && stateIdx < palette.size(),
                "state index must reference a palette entry"
        );
        CompoundTag campfireState = palette.getCompound(stateIdx);
        Assertions.assertEquals(
                "minecraft:campfire",
                campfireState.getString("Name"),
                "palette entry for campfire must have Name=minecraft:campfire"
        );
    }

    @Test
    void applyScaffolding_translatesEveryLayoutOffsetByFlagAnchor() {
        CompoundTag tag = freshStructureTag();
        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);

        ListTag blocks = result.getList("blocks", Tag.TAG_COMPOUND);
        List<ChickenScaffoldingLayout.BlockPlacement> plan =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);

        for (ChickenScaffoldingLayout.BlockPlacement placement : plan) {
            BlockPos translated = FLAG_ANCHOR.offset(placement.offset());
            BlockPos untranslated = placement.offset();
            Assertions.assertNotNull(
                    findBlockAt(blocks, translated),
                    "layout offset " + untranslated + " must land at FLAG_ANCHOR + offset = " + translated
            );
            // The untranslated position must not be there.
            if (!translated.equals(untranslated)) {
                Assertions.assertNull(
                        findBlockAt(blocks, untranslated),
                        "regression: editor wrote layout offset " + untranslated
                                + " at structure-local instead of translating by flag anchor"
                );
            }
        }
    }

    @Test
    void applyScaffolding_isIdempotent() {
        CompoundTag source = freshStructureTag();
        CompoundTag first = ChickenScaffoldingNbtEditor.applyScaffolding(source);
        CompoundTag second = ChickenScaffoldingNbtEditor.applyScaffolding(first);
        Assertions.assertTrue(
                NbtUtils.compareNbt(first, second, true),
                "running the editor twice must produce tag-equivalent output"
        );
    }

    @Test
    void applyScaffolding_doesNotMutateInputTag() {
        CompoundTag source = freshStructureTag();
        CompoundTag snapshot = source.copy();
        ChickenScaffoldingNbtEditor.applyScaffolding(source);
        Assertions.assertTrue(
                NbtUtils.compareNbt(snapshot, source, true),
                "applyScaffolding must not mutate its input"
        );
    }

    @Test
    void applyScaffolding_reusesExistingCobblestonePaletteEntry() {
        CompoundTag tag = freshStructureTag();
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        palette.add(NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState()));
        int initialSize = palette.size();

        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);
        ListTag finalPalette = result.getList("palette", Tag.TAG_COMPOUND);

        long cobbleEntries = 0;
        for (int i = 0; i < finalPalette.size(); i++) {
            CompoundTag entry = finalPalette.getCompound(i);
            if ("minecraft:cobblestone".equals(entry.getString("Name"))) {
                cobbleEntries++;
            }
        }
        Assertions.assertEquals(1, cobbleEntries,
                "editor must reuse existing cobblestone palette entry rather than duplicating");
        Assertions.assertTrue(
                finalPalette.size() >= initialSize,
                "palette can only grow, never shrink"
        );
    }

    @Test
    void applyScaffolding_appendsMissingCampfirePaletteEntry() {
        CompoundTag tag = freshStructureTag();
        int initialSize = tag.getList("palette", Tag.TAG_COMPOUND).size();
        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);

        ListTag palette = result.getList("palette", Tag.TAG_COMPOUND);
        long campfireEntries = 0;
        for (int i = 0; i < palette.size(); i++) {
            if ("minecraft:campfire".equals(palette.getCompound(i).getString("Name"))) {
                campfireEntries++;
            }
        }
        Assertions.assertEquals(1, campfireEntries);
        Assertions.assertTrue(palette.size() > initialSize,
                "palette must grow to include the appended campfire entry");
    }

    @Test
    void applyScaffolding_overwritesExistingBlockAtCampfireOffset() {
        CompoundTag tag = freshStructureTag();
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);

        BlockPos campfirePos = FLAG_ANCHOR.offset(ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets.CAMPFIRE_OFFSET);
        palette.add(NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));
        int dirtIdx = palette.size() - 1;
        CompoundTag existing = new CompoundTag();
        existing.putInt("state", dirtIdx);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(campfirePos.getX()));
        pos.add(IntTag.valueOf(campfirePos.getY()));
        pos.add(IntTag.valueOf(campfirePos.getZ()));
        existing.put("pos", pos);
        blocks.add(existing);

        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);
        ListTag finalBlocks = result.getList("blocks", Tag.TAG_COMPOUND);

        long blocksAtCampfire = 0;
        int finalStateIdx = -1;
        for (int i = 0; i < finalBlocks.size(); i++) {
            CompoundTag entry = finalBlocks.getCompound(i);
            BlockPos p = readPos(entry);
            if (campfirePos.equals(p)) {
                blocksAtCampfire++;
                finalStateIdx = entry.getInt("state");
            }
        }
        Assertions.assertEquals(1, blocksAtCampfire,
                "exactly one block entry must occupy flag + CAMPFIRE_OFFSET after apply");
        Assertions.assertEquals(
                "minecraft:campfire",
                result.getList("palette", Tag.TAG_COMPOUND)
                        .getCompound(finalStateIdx)
                        .getString("Name"),
                "final block at CAMPFIRE_OFFSET must be a campfire, not the seeded dirt"
        );
    }

    @Test
    void applyScaffolding_expandsSizeToEncloseAuthoredOffsets() {
        CompoundTag tag = freshStructureTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(1));
        size.add(IntTag.valueOf(2));
        tag.put("size", size);

        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);
        ListTag finalSize = result.getList("size", Tag.TAG_INT);
        Assertions.assertEquals(3, finalSize.size());

        int sx = finalSize.getInt(0);
        int sy = finalSize.getInt(1);
        int sz = finalSize.getInt(2);

        // Layout max (relative to the flag): x 6, y 1, z 9; plus the anchor, plus 1.
        int absMaxX = FLAG_ANCHOR.getX() + 6 + 1;
        int absMaxY = FLAG_ANCHOR.getY() + 1 + 1;
        int absMaxZ = FLAG_ANCHOR.getZ() + 9 + 1;
        Assertions.assertTrue(sx >= absMaxX, "sx=" + sx + " must enclose authored x (>=" + absMaxX + ")");
        Assertions.assertTrue(sy >= absMaxY, "sy=" + sy + " must enclose authored y (>=" + absMaxY + ")");
        Assertions.assertTrue(sz >= absMaxZ, "sz=" + sz + " must enclose authored z (>=" + absMaxZ + ")");
    }

    @Test
    void applyScaffolding_preservesExistingSizeWhenAlreadyLargeEnough() {
        CompoundTag tag = freshStructureTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(50));
        size.add(IntTag.valueOf(50));
        size.add(IntTag.valueOf(50));
        tag.put("size", size);

        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);
        ListTag finalSize = result.getList("size", Tag.TAG_INT);
        Assertions.assertEquals(50, finalSize.getInt(0));
        Assertions.assertEquals(50, finalSize.getInt(1));
        Assertions.assertEquals(50, finalSize.getInt(2));
    }

    @Test
    void run_apply_writesBackupAndUpdatedFile(@TempDir Path dir) throws IOException {
        Path structure = dir.resolve("empty_town.nbt");
        try (var out = Files.newOutputStream(structure)) {
            NbtIo.writeCompressed(freshStructureTag(), out);
        }

        int exit = ChickenScaffoldingNbtEditor.run(
                structure, ChickenScaffoldingNbtEditor.Mode.APPLY, true);
        Assertions.assertEquals(0, exit);

        Path backup = dir.resolve("empty_town.nbt.bak");
        Assertions.assertTrue(Files.exists(backup), "backup must be written before mutation");

        CompoundTag reloaded;
        try (var in = Files.newInputStream(structure)) {
            reloaded = NbtIo.readCompressed(in);
        }
        List<ChickenScaffoldingLayout.BlockPlacement> plan =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        Assertions.assertEquals(
                plan.size() + 1,
                reloaded.getList("blocks", Tag.TAG_COMPOUND).size()
        );
    }

    @Test
    void run_check_returnsNonZeroOnDrift(@TempDir Path dir) throws IOException {
        Path structure = dir.resolve("empty_town.nbt");
        try (var out = Files.newOutputStream(structure)) {
            NbtIo.writeCompressed(freshStructureTag(), out);
        }
        int exit = ChickenScaffoldingNbtEditor.run(
                structure, ChickenScaffoldingNbtEditor.Mode.CHECK, false);
        Assertions.assertEquals(1, exit, "empty structure is drifted from the target layout");
    }

    @Test
    void run_check_returnsZeroOnUpToDateFile(@TempDir Path dir) throws IOException {
        Path structure = dir.resolve("empty_town.nbt");
        CompoundTag applied = ChickenScaffoldingNbtEditor.applyScaffolding(freshStructureTag());
        try (var out = Files.newOutputStream(structure)) {
            NbtIo.writeCompressed(applied, out);
        }
        int exit = ChickenScaffoldingNbtEditor.run(
                structure, ChickenScaffoldingNbtEditor.Mode.CHECK, false);
        Assertions.assertEquals(0, exit);
    }

    /** Applies the editor to the real {@code empty_town.nbt}. Disabled; enable it, run it, commit the file, disable it again. */
    @Test
    @Disabled("On-demand tool; remove @Disabled to apply ChickenScaffoldingLayout to empty_town.nbt.")
    void applyToRealStructure() throws IOException {
        int exit = ChickenScaffoldingNbtEditor.run(
                ChickenScaffoldingNbtEditor.DEFAULT_STRUCTURE_PATH,
                ChickenScaffoldingNbtEditor.Mode.APPLY,
                true
        );
        Assertions.assertEquals(0, exit);
    }

    private static CompoundTag freshStructureTag() {
        CompoundTag tag = new CompoundTag();
        // The flag sits off the origin, so a layout written without translating fails.
        ListTag palette = new ListTag();
        CompoundTag flagPaletteEntry = new CompoundTag();
        flagPaletteEntry.putString("Name", "questown:cobblestone_flag_base");
        palette.add(flagPaletteEntry);
        tag.put("palette", palette);

        ListTag blocks = new ListTag();
        CompoundTag flagBlock = new CompoundTag();
        flagBlock.putInt("state", 0);
        ListTag flagPos = new ListTag();
        flagPos.add(IntTag.valueOf(FLAG_ANCHOR.getX()));
        flagPos.add(IntTag.valueOf(FLAG_ANCHOR.getY()));
        flagPos.add(IntTag.valueOf(FLAG_ANCHOR.getZ()));
        flagBlock.put("pos", flagPos);
        blocks.add(flagBlock);
        tag.put("blocks", blocks);

        ListTag size = new ListTag();
        size.add(IntTag.valueOf(32));
        size.add(IntTag.valueOf(8));
        size.add(IntTag.valueOf(32));
        tag.put("size", size);
        tag.putInt("DataVersion", 3120);
        return tag;
    }

    private static boolean containsBlockAt(ListTag blocks, BlockPos pos) {
        return findBlockAt(blocks, pos) != null;
    }

    private static CompoundTag findBlockAt(ListTag blocks, BlockPos target) {
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            BlockPos p = readPos(entry);
            if (target.equals(p)) {
                return entry;
            }
        }
        return null;
    }

    private static BlockPos readPos(CompoundTag blockEntry) {
        ListTag list = blockEntry.getList("pos", Tag.TAG_INT);
        if (list.size() != 3) {
            return null;
        }
        return new BlockPos(list.getInt(0), list.getInt(1), list.getInt(2));
    }
}
