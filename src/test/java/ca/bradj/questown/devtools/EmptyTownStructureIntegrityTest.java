package ca.bradj.questown.devtools;

import ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Checks the committed {@code empty_town.nbt} itself, including the campfire's place relative to the flag. */
class EmptyTownStructureIntegrityTest {

    private static final Path STRUCTURE = Path.of(
            "src/main/resources/data/questown/structures/empty_town.nbt"
    );

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void emptyTownHasTerrainShaperLayersBaked() throws IOException {
        CompoundTag root = NbtIo.readCompressed(Files.newInputStream(STRUCTURE));
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);

        List<Integer> dirtIdx = paletteIndicesMatching(palette, "minecraft:dirt"::equals);
        List<Integer> airIdx = paletteIndicesMatching(palette, "minecraft:air"::equals);
        List<Integer> grassBlockIdx = paletteIndicesMatching(palette, "minecraft:grass_block"::equals);

        Assertions.assertFalse(dirtIdx.isEmpty(),
                "dirt floor underlayer must be present (regenerate via EmptyTownTerrainShaperTest.applyToRealStructure)");
        Assertions.assertFalse(airIdx.isEmpty(),
                "air ceiling must be present");
        Assertions.assertFalse(grassBlockIdx.isEmpty(),
                "grass_block cascade tops must be present");

        long dirtCount = countBlocksWithStateIn(blocks, dirtIdx);
        long airCount = countBlocksWithStateIn(blocks, airIdx);
        long grassBlockCount = countBlocksWithStateIn(blocks, grassBlockIdx);

        // Lower bounds: well below these, a layer is missing.
        Assertions.assertTrue(dirtCount > 1000,
                "expected >1000 dirt entries (floor + cascade body), found " + dirtCount);
        Assertions.assertTrue(airCount > 5000,
                "expected >5000 air entries (16-layer ceiling over footprint), found " + airCount);
        Assertions.assertTrue(grassBlockCount > 100,
                "expected >100 grass_block entries (cascade tops + plane surface), found " + grassBlockCount);
    }

    @Test
    void emptyTownHasExactlyOneCampfireAtFlagPlusCampfireOffset() throws IOException {
        CompoundTag root = NbtIo.readCompressed(Files.newInputStream(STRUCTURE));
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);

        List<Integer> flagIndices = paletteIndicesMatching(palette, name ->
                name.startsWith("questown:") && name.endsWith("_flag_base"));
        List<Integer> campfireIndices = paletteIndicesMatching(palette, "minecraft:campfire"::equals);

        BlockPos flagPos = singletonBlockPosForIndices(blocks, flagIndices,
                "exactly one town flag block must exist in empty_town.nbt");
        List<BlockPos> campfirePositions = blockPositionsForIndices(blocks, campfireIndices);

        Assertions.assertEquals(
                1, campfirePositions.size(),
                "empty_town.nbt must contain exactly one campfire (the chicken-arc anchor); found "
                        + campfirePositions.size() + " at " + campfirePositions
        );
        BlockPos expected = flagPos.offset(HelperChickenBeatOffsets.CAMPFIRE_OFFSET);
        Assertions.assertEquals(
                expected,
                campfirePositions.get(0),
                "campfire must sit at flag " + flagPos + " + CAMPFIRE_OFFSET "
                        + HelperChickenBeatOffsets.CAMPFIRE_OFFSET + " = " + expected
                        + " — the rotation detector scans this position. "
                        + "If this assertion fails, regenerate empty_town.nbt via "
                        + "ChickenScaffoldingNbtEditorTest.applyToRealStructure."
        );
    }

    private static List<Integer> paletteIndicesMatching(ListTag palette, java.util.function.Predicate<String> nameMatch) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            if (nameMatch.test(palette.getCompound(i).getString("Name"))) {
                out.add(i);
            }
        }
        return out;
    }

    private static BlockPos singletonBlockPosForIndices(ListTag blocks, List<Integer> indices, String message) {
        List<BlockPos> matches = blockPositionsForIndices(blocks, indices);
        Assertions.assertEquals(1, matches.size(),
                message + " (found " + matches.size() + " at " + matches + ")");
        return matches.get(0);
    }

    private static long countBlocksWithStateIn(ListTag blocks, List<Integer> indices) {
        long count = 0;
        for (int i = 0; i < blocks.size(); i++) {
            if (indices.contains(blocks.getCompound(i).getInt("state"))) {
                count++;
            }
        }
        return count;
    }

    private static List<BlockPos> blockPositionsForIndices(ListTag blocks, List<Integer> indices) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            if (indices.contains(entry.getInt("state"))) {
                ListTag posList = entry.getList("pos", Tag.TAG_INT);
                out.add(new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2)));
            }
        }
        return out;
    }
}
