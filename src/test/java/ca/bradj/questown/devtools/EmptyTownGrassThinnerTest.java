package ca.bradj.questown.devtools;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class EmptyTownGrassThinnerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void thinGrass_lowersTallGrassAndDeletesUpperHalves() {
        CompoundTag tag = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(tallGrassPaletteEntry("lower"));   // idx 0
        palette.add(tallGrassPaletteEntry("upper"));   // idx 1
        tag.put("palette", palette);

        ListTag blocks = new ListTag();
        // 8 tall_grass plants = 8 lower + 8 upper. Keep RNG sample size healthy
        // so the post-thin count is far enough from the pass/fail boundary
        // that the deterministic seed's hits don't make the assertion brittle.
        for (int i = 0; i < 8; i++) {
            blocks.add(blockEntry(0, i, 64, 0));
            blocks.add(blockEntry(1, i, 65, 0));
        }
        tag.put("blocks", blocks);

        CompoundTag result = EmptyTownGrassThinner.thinGrass(tag);
        ListTag finalBlocks = result.getList("blocks", Tag.TAG_COMPOUND);

        ListTag finalPalette = result.getList("palette", Tag.TAG_COMPOUND);
        int grassIdx = -1;
        for (int i = 0; i < finalPalette.size(); i++) {
            CompoundTag p = finalPalette.getCompound(i);
            if ("minecraft:grass".equals(p.getString("Name")) && !p.contains("Properties")) {
                grassIdx = i;
                break;
            }
        }
        Assertions.assertTrue(grassIdx >= 0, "grass palette entry must exist after thinning");

        int grassCount = 0;
        int tallLowerCount = 0;
        int tallUpperCount = 0;
        for (int i = 0; i < finalBlocks.size(); i++) {
            int s = finalBlocks.getCompound(i).getInt("state");
            if (s == grassIdx) grassCount++;
            else if (s == 0) tallLowerCount++;
            else if (s == 1) tallUpperCount++;
        }
        Assertions.assertEquals(0, tallLowerCount, "no tall_grass lower halves should remain");
        Assertions.assertEquals(0, tallUpperCount, "no tall_grass upper halves should remain");
        Assertions.assertTrue(grassCount > 0 && grassCount < 8,
                "expected ~50% grass survival from 8 lowered tall_grass; got " + grassCount);
    }

    @Test
    void thinGrass_isReproducibleAcrossRuns() {
        CompoundTag tag = sampleTagWithGrass(40);
        CompoundTag a = EmptyTownGrassThinner.thinGrass(tag);
        CompoundTag b = EmptyTownGrassThinner.thinGrass(tag);
        Assertions.assertTrue(NbtUtils.compareNbt(a, b, true),
                "fixed-seed thinning must produce identical output across runs");
    }

    @Test
    void thinGrass_doesNotMutateInput() {
        CompoundTag tag = sampleTagWithGrass(20);
        CompoundTag snapshot = tag.copy();
        EmptyTownGrassThinner.thinGrass(tag);
        Assertions.assertTrue(NbtUtils.compareNbt(snapshot, tag, true),
                "thinGrass must not mutate its input");
    }

    /**
     * Opt-in: actually rewrite the committed empty_town.nbt. Same pattern as
     * {@code ChickenScaffoldingNbtEditorTest.applyToRealStructure} — remove
     * {@code @Disabled}, run this single test, commit the file change,
     * restore {@code @Disabled}.
     */
    @Test
    @Disabled("On-demand tool; remove @Disabled to thin grass in the real empty_town.nbt.")
    void applyToRealStructure() throws IOException {
        int exit = EmptyTownGrassThinner.run(
                EmptyTownGrassThinner.DEFAULT_STRUCTURE_PATH,
                EmptyTownGrassThinner.Mode.LOWER_AND_THIN,
                true
        );
        Assertions.assertEquals(0, exit);
    }

    @Test
    @Disabled("On-demand tool; remove @Disabled to lower tall_grass in the real empty_town.nbt without thinning.")
    void applyLowerOnlyToRealStructure() throws IOException {
        int exit = EmptyTownGrassThinner.run(
                EmptyTownGrassThinner.DEFAULT_STRUCTURE_PATH,
                EmptyTownGrassThinner.Mode.LOWER_ONLY,
                true
        );
        Assertions.assertEquals(0, exit);
    }

    // -- helpers ---------------------------------------------------------

    private static CompoundTag tallGrassPaletteEntry(String half) {
        CompoundTag e = new CompoundTag();
        e.putString("Name", "minecraft:tall_grass");
        CompoundTag props = new CompoundTag();
        props.putString("half", half);
        e.put("Properties", props);
        return e;
    }

    private static CompoundTag blockEntry(int stateIdx, int x, int y, int z) {
        CompoundTag e = new CompoundTag();
        e.putInt("state", stateIdx);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        e.put("pos", pos);
        return e;
    }

    private static CompoundTag sampleTagWithGrass(int n) {
        CompoundTag tag = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.GRASS.defaultBlockState())); // idx 0
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int i = 0; i < n; i++) {
            blocks.add(blockEntry(0, i, 64, 0));
        }
        tag.put("blocks", blocks);
        return tag;
    }
}
