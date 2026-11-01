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

class EmptyTownGrassPrunerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepZone_includesFlagRingAndRoomRing() {
        // Flag-adjacent (Chebyshev <= 1 from origin)
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(0, 0));
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(-1, 0));
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(1, 1));
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(-1, -1));

        // Room-adjacent ring (envelope x∈[1..7], z∈[1..7])
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(1, 4));
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(7, 4));
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(4, 1));
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(4, 7));
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(7, 7));
        // Room interior is also inside the envelope — that's fine; grass
        // inside the room would be pruned by scaffolding anyway.
        Assertions.assertTrue(EmptyTownGrassPruner.isInKeepZone(4, 4));
    }

    @Test
    void keepZone_excludesDistantCells() {
        Assertions.assertFalse(EmptyTownGrassPruner.isInKeepZone(0, 8));
        Assertions.assertFalse(EmptyTownGrassPruner.isInKeepZone(8, 4));
        Assertions.assertFalse(EmptyTownGrassPruner.isInKeepZone(-2, 0));
        Assertions.assertFalse(EmptyTownGrassPruner.isInKeepZone(0, -2));
        Assertions.assertFalse(EmptyTownGrassPruner.isInKeepZone(-3, -3));
        // Beyond the room ring on the +x/+z corner
        Assertions.assertFalse(EmptyTownGrassPruner.isInKeepZone(8, 8));
    }

    @Test
    void pruneDistantGrass_keepsNearGrassAndRemovesFarGrass() {
        CompoundTag tag = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.GRASS.defaultBlockState())); // idx 0
        // Real Questown flag block name; the pruner finds it by suffix match.
        palette.add(flagBasePaletteEntry()); // idx 1
        tag.put("palette", palette);

        // Place flag at a non-zero anchor to verify the relative-coord math.
        int fx = 10, fy = 64, fz = 20;
        ListTag blocks = new ListTag();
        blocks.add(blockEntry(1, fx, fy, fz));
        // KEEP — adjacent to flag (dx=-1, dz=0)
        blocks.add(blockEntry(0, fx - 1, fy, fz));
        // KEEP — adjacent to room ring (dx=1, dz=4)
        blocks.add(blockEntry(0, fx + 1, fy, fz + 4));
        // KEEP — corner of room envelope (dx=7, dz=7)
        blocks.add(blockEntry(0, fx + 7, fy, fz + 7));
        // REMOVE — well beyond the ring
        blocks.add(blockEntry(0, fx + 12, fy, fz + 12));
        blocks.add(blockEntry(0, fx - 5, fy, fz));
        blocks.add(blockEntry(0, fx, fy, fz - 5));
        tag.put("blocks", blocks);

        CompoundTag result = EmptyTownGrassPruner.pruneDistantGrass(tag);
        ListTag finalBlocks = result.getList("blocks", Tag.TAG_COMPOUND);

        int grassCount = 0;
        for (int i = 0; i < finalBlocks.size(); i++) {
            if (finalBlocks.getCompound(i).getInt("state") == 0) {
                grassCount++;
            }
        }
        Assertions.assertEquals(3, grassCount,
                "expected 3 near-grass blocks kept, far-grass removed; got " + grassCount);
    }

    @Test
    void pruneDistantGrass_doesNotMutateInput() {
        CompoundTag tag = sampleTag();
        CompoundTag snapshot = tag.copy();
        EmptyTownGrassPruner.pruneDistantGrass(tag);
        Assertions.assertTrue(NbtUtils.compareNbt(snapshot, tag, true),
                "pruneDistantGrass must not mutate its input");
    }

    @Test
    void pruneDistantGrass_alsoRemovesTallGrass() {
        CompoundTag tag = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(tallGrassPaletteEntry()); // idx 0
        palette.add(flagBasePaletteEntry()); // idx 1
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(blockEntry(1, 0, 64, 0));
        blocks.add(blockEntry(0, 20, 64, 20)); // far away
        tag.put("blocks", blocks);

        CompoundTag result = EmptyTownGrassPruner.pruneDistantGrass(tag);
        ListTag finalBlocks = result.getList("blocks", Tag.TAG_COMPOUND);
        Assertions.assertEquals(1, finalBlocks.size(),
                "only the flag should remain — distant tall_grass must be pruned");
    }

    /**
     * Opt-in: actually rewrite the committed empty_town.nbt. Same pattern as
     * the other devtools — remove {@code @Disabled}, run this single test,
     * commit the file change, restore {@code @Disabled}.
     */
    @Test
    @Disabled("On-demand tool; remove @Disabled to prune distant grass in the real empty_town.nbt.")
    void applyToRealStructure() throws IOException {
        int exit = EmptyTownGrassPruner.run(EmptyTownGrassPruner.DEFAULT_STRUCTURE_PATH, true);
        Assertions.assertEquals(0, exit);
    }

    // -- helpers ---------------------------------------------------------

    private static CompoundTag tallGrassPaletteEntry() {
        CompoundTag e = new CompoundTag();
        e.putString("Name", "minecraft:tall_grass");
        CompoundTag props = new CompoundTag();
        props.putString("half", "lower");
        e.put("Properties", props);
        return e;
    }

    private static CompoundTag flagBasePaletteEntry() {
        CompoundTag e = new CompoundTag();
        e.putString("Name", "questown:second_flag_base");
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

    private static CompoundTag sampleTag() {
        CompoundTag tag = new CompoundTag();
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.GRASS.defaultBlockState()));
        palette.add(flagBasePaletteEntry());
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(blockEntry(1, 0, 64, 0));
        for (int i = 0; i < 10; i++) {
            blocks.add(blockEntry(0, i * 2, 64, i * 2));
        }
        tag.put("blocks", blocks);
        return tag;
    }
}
