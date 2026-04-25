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

/**
 * Resource-level invariants for the committed {@code empty_town.nbt}. These
 * run on every {@code ./gradlew test} so a regression in the structure file
 * itself fails CI, not just regressions in the editor logic that produced it.
 *
 * <p>The original chicken-arc bug landed because the editor wrote layout
 * offsets directly to structure-local instead of translating by the flag's
 * structure-local anchor. {@link
 * ChickenScaffoldingNbtEditorTest#applyScaffolding_translatesEveryLayoutOffsetByFlagAnchor}
 * locks the editor down. This test locks the resulting resource file down —
 * any future edit (manual NBT surgery, schema migration, structure block
 * round-trip) that breaks the campfire's position relative to the flag
 * fails here.
 */
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
