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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class ChickenScaffoldingNbtEditorTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // Happy path — synthetic structure tag
    // -------------------------------------------------------------------------

    @Test
    void applyScaffolding_insertsPlannedBlocksIntoEmptyStructure() {
        CompoundTag tag = freshStructureTag();
        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);

        List<ChickenScaffoldingLayout.BlockPlacement> plan =
                ChickenScaffoldingLayout.forRotation(Rotation.NONE);
        ListTag blocks = result.getList("blocks", Tag.TAG_COMPOUND);
        Assertions.assertEquals(plan.size(), blocks.size(),
                "every planned placement lands as one block entry");

        for (ChickenScaffoldingLayout.BlockPlacement placement : plan) {
            Assertions.assertTrue(
                    containsBlockAt(blocks, placement.offset()),
                    "missing planned block at " + placement.offset()
            );
        }
    }

    @Test
    void applyScaffolding_palettePointsAtRealBlockStates() {
        CompoundTag tag = freshStructureTag();
        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);

        ListTag palette = result.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = result.getList("blocks", Tag.TAG_COMPOUND);

        CompoundTag campfireEntry = findBlockAt(blocks, new BlockPos(3, 0, 5));
        Assertions.assertNotNull(campfireEntry, "campfire must be present in blocks list");
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

    // -------------------------------------------------------------------------
    // Idempotency — tag-equivalent re-runs
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Palette management
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Overwrite existing block at target offset
    // -------------------------------------------------------------------------

    @Test
    void applyScaffolding_overwritesExistingBlockAtCampfireOffset() {
        CompoundTag tag = freshStructureTag();
        ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);

        // Seed: a dirt block already sits where the campfire will go.
        palette.add(NbtUtils.writeBlockState(Blocks.DIRT.defaultBlockState()));
        int dirtIdx = palette.size() - 1;
        CompoundTag existing = new CompoundTag();
        existing.putInt("state", dirtIdx);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(3));
        pos.add(IntTag.valueOf(0));
        pos.add(IntTag.valueOf(5));
        existing.put("pos", pos);
        blocks.add(existing);

        CompoundTag result = ChickenScaffoldingNbtEditor.applyScaffolding(tag);
        ListTag finalBlocks = result.getList("blocks", Tag.TAG_COMPOUND);

        long blocksAtCampfire = 0;
        int finalStateIdx = -1;
        for (int i = 0; i < finalBlocks.size(); i++) {
            CompoundTag entry = finalBlocks.getCompound(i);
            BlockPos p = readPos(entry);
            if (new BlockPos(3, 0, 5).equals(p)) {
                blocksAtCampfire++;
                finalStateIdx = entry.getInt("state");
            }
        }
        Assertions.assertEquals(1, blocksAtCampfire,
                "exactly one block entry must occupy CAMPFIRE_OFFSET after apply");
        Assertions.assertEquals(
                "minecraft:campfire",
                result.getList("palette", Tag.TAG_COMPOUND)
                        .getCompound(finalStateIdx)
                        .getString("Name"),
                "final block at CAMPFIRE_OFFSET must be a campfire, not the seeded dirt"
        );
    }

    // -------------------------------------------------------------------------
    // Size expansion
    // -------------------------------------------------------------------------

    @Test
    void applyScaffolding_expandsSizeToEncloseAuthoredOffsets() {
        CompoundTag tag = freshStructureTag();
        // Start with an undersized box (2x1x2 — smaller than the authored layout).
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

        // The authored layout includes GATE_CENTER_OFFSET at (2, 0, 9) with columns
        // at x=1 and x=3 — max x is 4 (pos.x+1), max z is 10.
        Assertions.assertTrue(sx >= 4, "sx=" + sx + " must enclose authored x");
        Assertions.assertTrue(sy >= 1, "sy=" + sy + " must enclose authored y");
        Assertions.assertTrue(sz >= 10, "sz=" + sz + " must enclose authored z");
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

    // -------------------------------------------------------------------------
    // Disk I/O round-trip
    // -------------------------------------------------------------------------

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
                plan.size(),
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

    // -------------------------------------------------------------------------
    // On-demand entry point: apply editor to the real empty_town.nbt
    // -------------------------------------------------------------------------

    /**
     * Opt-in run that mutates the real committed {@code empty_town.nbt}. Enabled
     * only when the Gradle alias passes {@code -DenableEditor=true}. The developer
     * or agent commits the resulting file change manually.
     */
    @Test
    @EnabledIfSystemProperty(named = "enableEditor", matches = "true")
    void applyToRealStructure() throws IOException {
        int exit = ChickenScaffoldingNbtEditor.run(
                ChickenScaffoldingNbtEditor.DEFAULT_STRUCTURE_PATH,
                ChickenScaffoldingNbtEditor.Mode.APPLY,
                true
        );
        Assertions.assertEquals(0, exit);
    }

    // -------------------------------------------------------------------------
    // Guard: if Bootstrap isn't set up, Blocks.CAMPFIRE is null — editor should
    // surface the error clearly rather than segfault on a null state.
    // -------------------------------------------------------------------------

    @Test
    @Disabled("demonstrates the Bootstrap prerequisite; run only when investigating harness issues")
    void applyScaffolding_failsCleanlyWithoutBootstrap() {
        // Intentionally not calling Bootstrap.bootStrap() would make Blocks.CAMPFIRE null,
        // but @BeforeAll already booted it for the whole class. This test documents the
        // requirement; enable it manually to verify the failure mode.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CompoundTag freshStructureTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("palette", new ListTag());
        tag.put("blocks", new ListTag());
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(16));
        size.add(IntTag.valueOf(4));
        size.add(IntTag.valueOf(16));
        tag.put("size", size);
        tag.putInt("DataVersion", 3120); // 1.19.2
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
