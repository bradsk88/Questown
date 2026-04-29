package ca.bradj.questown.devtools;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
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
import java.util.HashMap;
import java.util.Map;

class EmptyTownTerrainShaperTest {

    private static final int ORIG_SX = 6;
    private static final int ORIG_SY = 5;
    private static final int ORIG_SZ = 6;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // Geometry — size, payload preservation, ceiling, floor, cascade
    // -------------------------------------------------------------------------

    @Test
    void shape_sizeExpandsByCascadeAndCeiling() {
        CompoundTag tag = sampleStructure();
        CompoundTag result = EmptyTownTerrainShaper.shape(tag);
        ListTag size = result.getList("size", Tag.TAG_INT);
        Assertions.assertEquals(
                ORIG_SX + 2 * EmptyTownTerrainShaper.CASCADE_DISTANCE, size.getInt(0));
        Assertions.assertEquals(
                ORIG_SY + EmptyTownTerrainShaper.FLOOR_DEPTH + EmptyTownTerrainShaper.CEILING_HEIGHT,
                size.getInt(1));
        Assertions.assertEquals(
                ORIG_SZ + 2 * EmptyTownTerrainShaper.CASCADE_DISTANCE, size.getInt(2));
    }

    @Test
    void shape_originalPayloadShiftsButPalettePreserved() {
        CompoundTag tag = sampleStructure();
        CompoundTag result = EmptyTownTerrainShaper.shape(tag);
        // Original sample has a stone block at (1, 0, 1) and a glass at (4, 4, 4).
        // After shift by (CASCADE, FLOOR, CASCADE): (4, 3, 4) and (7, 7, 7).
        Assertions.assertNotNull(
                findBlockOfPalette(result, 4, 3, 4, "minecraft:stone"));
        Assertions.assertNotNull(
                findBlockOfPalette(result, 7, 7, 7, "minecraft:glass"));
    }

    @Test
    void shape_floorFillsDirtUnderOriginalFootprint() {
        CompoundTag tag = sampleStructure();
        CompoundTag result = EmptyTownTerrainShaper.shape(tag);
        int x0 = EmptyTownTerrainShaper.CASCADE_DISTANCE;
        int z0 = EmptyTownTerrainShaper.CASCADE_DISTANCE;
        int x1 = x0 + ORIG_SX - 1;
        int z1 = z0 + ORIG_SZ - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 0; y < EmptyTownTerrainShaper.FLOOR_DEPTH; y++) {
                    Assertions.assertNotNull(
                            findBlockOfPalette(result, x, y, z, "minecraft:dirt"),
                            "missing dirt floor at " + x + "," + y + "," + z
                    );
                }
            }
        }
    }

    @Test
    void shape_ceilingFillsAirAboveOriginalFootprint() {
        CompoundTag tag = sampleStructure();
        CompoundTag result = EmptyTownTerrainShaper.shape(tag);
        int yTop = ORIG_SY - 1 + EmptyTownTerrainShaper.FLOOR_DEPTH;
        int x0 = EmptyTownTerrainShaper.CASCADE_DISTANCE;
        int z0 = EmptyTownTerrainShaper.CASCADE_DISTANCE;
        int x1 = x0 + ORIG_SX - 1;
        int z1 = z0 + ORIG_SZ - 1;
        for (int dy = 1; dy <= EmptyTownTerrainShaper.CEILING_HEIGHT; dy++) {
            int y = yTop + dy;
            // Spot check four corners + center at this Y.
            for (int[] xz : new int[][]{
                    {x0, z0}, {x1, z1}, {x0, z1}, {x1, z0}, {(x0 + x1) / 2, (z0 + z1) / 2}
            }) {
                Assertions.assertNotNull(
                        findBlockOfPalette(result, xz[0], y, xz[1], "minecraft:air"),
                        "missing air ceiling at " + xz[0] + "," + y + "," + xz[1]
                );
            }
        }
    }

    @Test
    void shape_cascadePlacesGrassToppedDirtAtDecreasingY() {
        CompoundTag tag = sampleStructure();
        CompoundTag result = EmptyTownTerrainShaper.shape(tag);
        int planeGroundY = EmptyTownTerrainShaper.FLOOR_DEPTH;
        int x0 = EmptyTownTerrainShaper.CASCADE_DISTANCE;
        int z0 = EmptyTownTerrainShaper.CASCADE_DISTANCE;
        int x1 = x0 + ORIG_SX - 1;
        int z1 = z0 + ORIG_SZ - 1;

        // Ring r=1 directly outside the footprint on the +x edge: top grass at planeGroundY-1.
        int sampleX = x1 + 1;
        int sampleZ = (z0 + z1) / 2;
        Assertions.assertNotNull(
                findBlockOfPalette(result, sampleX, planeGroundY - 1, sampleZ, "minecraft:grass_block"),
                "ring 1 grass top missing at " + sampleX + "," + (planeGroundY - 1) + "," + sampleZ
        );
        // Ring r=2: 2 cells out, top at planeGroundY-2.
        Assertions.assertNotNull(
                findBlockOfPalette(result, x1 + 2, planeGroundY - 2, sampleZ, "minecraft:grass_block")
        );
        // Ring r=3: 3 cells out, top at planeGroundY-3 = 0.
        Assertions.assertNotNull(
                findBlockOfPalette(result, x1 + 3, 0, sampleZ, "minecraft:grass_block")
        );
        // Below the ring 1 grass top there should be dirt at Y=0..planeGroundY-2.
        for (int y = 0; y < planeGroundY - 1; y++) {
            Assertions.assertNotNull(
                    findBlockOfPalette(result, sampleX, y, sampleZ, "minecraft:dirt"),
                    "ring 1 dirt missing under grass at y=" + y
            );
        }
    }

    @Test
    void shape_doesNotMutateInput() {
        CompoundTag tag = sampleStructure();
        CompoundTag snapshot = tag.copy();
        EmptyTownTerrainShaper.shape(tag);
        Assertions.assertTrue(NbtUtils.compareNbt(snapshot, tag, true),
                "shape() must not mutate its input");
    }

    @Test
    void shape_isReproducible() {
        CompoundTag tag = sampleStructure();
        CompoundTag a = EmptyTownTerrainShaper.shape(tag);
        CompoundTag b = EmptyTownTerrainShaper.shape(tag);
        Assertions.assertTrue(NbtUtils.compareNbt(a, b, true),
                "deterministic shaper must produce identical output across runs");
    }

    // -------------------------------------------------------------------------
    // On-demand: actually rewrite the committed empty_town.nbt
    // -------------------------------------------------------------------------

    @Test
    @Disabled("On-demand tool; remove @Disabled to reshape the real empty_town.nbt.")
    void applyToRealStructure() throws IOException {
        int exit = EmptyTownTerrainShaper.run(EmptyTownTerrainShaper.DEFAULT_STRUCTURE_PATH, true);
        Assertions.assertEquals(0, exit);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * 6×5×6 cube with stone at (1,0,1), glass at (4,4,4), and an empty palette
     * stub for everything else. Lets the assertions check shift correctness
     * without bootstrapping the real empty_town.
     */
    private static CompoundTag sampleStructure() {
        CompoundTag root = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState())); // 0
        palette.add(NbtUtils.writeBlockState(Blocks.GLASS.defaultBlockState())); // 1
        // Strip Properties so ensurePaletteEntry's reuse check sees the bare entries.
        for (int i = 0; i < palette.size(); i++) {
            palette.getCompound(i).remove("Properties");
        }
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        blocks.add(blockEntry(0, 1, 0, 1));
        blocks.add(blockEntry(1, 4, 4, 4));
        root.put("blocks", blocks);

        ListTag size = new ListTag();
        size.add(IntTag.valueOf(ORIG_SX));
        size.add(IntTag.valueOf(ORIG_SY));
        size.add(IntTag.valueOf(ORIG_SZ));
        root.put("size", size);
        root.putInt("DataVersion", 3120);
        return root;
    }

    private static CompoundTag blockEntry(int stateIdx, int x, int y, int z) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("state", stateIdx);
        ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(x));
        pos.add(IntTag.valueOf(y));
        pos.add(IntTag.valueOf(z));
        entry.put("pos", pos);
        return entry;
    }

    /**
     * Looks up the palette name of the block at {@code (x,y,z)}. Returns the
     * matching block entry tag if a block of {@code expectedName} is found
     * there, else null. Uses a per-call palette index so callers don't need
     * to know palette layout.
     */
    private static CompoundTag findBlockOfPalette(
            CompoundTag root, int x, int y, int z, String expectedName
    ) {
        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        Map<Integer, String> namesByIdx = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) {
            namesByIdx.put(i, palette.getCompound(i).getString("Name"));
        }
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag entry = blocks.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            if (pos.size() != 3) {
                continue;
            }
            if (pos.getInt(0) == x && pos.getInt(1) == y && pos.getInt(2) == z
                    && expectedName.equals(namesByIdx.get(entry.getInt("state")))) {
                return entry;
            }
        }
        return null;
    }
}
