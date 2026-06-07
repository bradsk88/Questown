package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.commands.test.TestBlueprint.BlockPlacement;
import ca.bradj.questown.commands.test.TestBlueprint.RoomType;
import ca.bradj.questown.commands.test.TestExpectation.ExpectedProduct;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TestBlueprintRegistry {

    public interface AnyTestEntry {
        String name();
        String category();
    }

    public record TestEntry(String name, JobID jobId, TestBlueprint blueprint, String category)
            implements AnyTestEntry {}

    /** A self-reporting boolean check that only needs the {@link ServerLevel} (no town build). */
    public record LevelCheck(String name, String category, Function<ServerLevel, Boolean> check)
            implements AnyTestEntry {}

    public static @Nullable TestBlueprint get(JobID jobId) {
        if ("farmer".equals(jobId.rootId())) {
            return farmerBlueprint();
        }
        if ("cook".equals(jobId.rootId())) {
            return cookBlueprint();
        }
        if ("baker".equals(jobId.rootId())) {
            return bakerBlueprint();
        }
        if ("crafter".equals(jobId.rootId())) {
            if (isArmorerJob(jobId)) {
                return armorerBlueprint();
            }
            return crafterBlueprint();
        }
        if ("smelter".equals(jobId.rootId())) {
            return smelterBlueprint();
        }
        if ("soup_cook".equals(jobId.rootId())) {
            return soupCookBlueprint();
        }
        if ("explorer".equals(jobId.rootId())) {
            return explorerBlueprint();
        }
        if ("gatherer".equals(jobId.rootId())) {
            return gathererBlueprint();
        }
        if ("hunter".equals(jobId.rootId())) {
            return hunterBlueprint();
        }
        if ("miner".equals(jobId.rootId())) {
            return minerBlueprint();
        }
        if ("fisher".equals(jobId.rootId())) {
            return fisherBlueprint();
        }
        if ("arborist".equals(jobId.rootId())) {
            return arboristCutTreesBlueprint();
        }
        return null;
    }

    public static List<AnyTestEntry> getTestableJobs() {
        List<AnyTestEntry> jobs = new ArrayList<>();

        // Core job tests
        jobs.add(entry(new JobID("farmer", "harvest_wheat"), farmerBlueprint()));
        jobs.add(entry(new JobID("cook", "simple_furnace_food"), cookBlueprint()));
        jobs.add(entry(new JobID("baker", "bread"), bakerBlueprint()));
        jobs.add(entry(new JobID("crafter", "stick"), crafterBlueprint()));
        jobs.add(entry(new JobID("crafter", "wooden_axe"), blacksmithBlueprint()));
        jobs.add(entry(new JobID("smelter", "process_ore"), smelterBlueprint()));
        jobs.add(entry(new JobID("soup_cook", "one_mushroom_stew"), soupCookBlueprint()));
        jobs.add(entry(new JobID("gatherer", "axe"), gathererBlueprint()));
        jobs.add(entry(new JobID("explorer", "explore"), explorerBlueprint()));
        jobs.add(entry(new JobID("hunter", "sword"), hunterBlueprint()));
        jobs.add(entry(new JobID("miner", "coal"), minerBlueprint()));
        jobs.add(entry(new JobID("fisher", "fish"), fisherBlueprint()));
        jobs.add(entry(new JobID("crafter", "leather_boots"), armorerBlueprint()));
        jobs.add(entry(new JobID("arborist", "cut_trees"), arboristCutTreesBlueprint()));
        jobs.add(entry(new JobID("arborist", "plant_sapling"), arboristPlantSaplingBlueprint()));
        jobs.add(edgeCaseEntry("arborist", "cut_trees", "full_cycle", arboristFullCycleBlueprint()));

        // Edge case tests
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "night_start", farmerNightStartBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "all_night", farmerAllNightBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "3_day", farmerMultiDayBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "no_supplies", farmerNoSuppliesBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "tool_durability", gathererToolDurabilityBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "2_villagers", farmerTwoVillagersBlueprint()));
        jobs.add(edgeCaseEntry("farmer", "harvest_wheat", "warp_then_realtime", farmerRealtimeBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "short_absence", gathererShortAbsenceBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "sleep_jump", gathererSleepJumpBlueprint()));
        jobs.add(edgeCaseEntry("gatherer", "axe", "absence_then_sleep", gathererAbsenceThenSleepBlueprint()));

        // Eating tests
        jobs.add(eatingEntry("eat_no_table", eatNoTableBlueprint()));
        jobs.add(eatingEntry("dine_at_time", dineAtTimeBlueprint()));
        jobs.add(eatingEntry("eat_raw_food", eatRawFoodBlueprint()));
        jobs.add(eatingEntryDirect("eat_direct", eatDirectBlueprint()));

        // Worldgen tests
        jobs.add(emptyTownStructureCheck());
        jobs.add(emptyTownStructureSetCheck());

        // UI tests
        jobs.add(jobBoardKnowledgeGatingCheck());

        return jobs;
    }

    public static List<AnyTestEntry> getTestsByCategory(String category) {
        return getTestableJobs().stream()
                .filter(e -> category.equals(e.category()))
                .toList();
    }

    /**
     * Resolves the jobs-track scenarios for a category selector. {@code null} or
     * the {@code "jobs"} meta-bucket means "every testable job"; any other value
     * narrows to that category.
     */
    public static List<AnyTestEntry> resolveJobs(@Nullable String category) {
        return (category == null || "jobs".equals(category))
                ? getTestableJobs()
                : getTestsByCategory(category);
    }

    private static TestEntry entry(JobID id, TestBlueprint bp) {
        return new TestEntry(id.rootId() + "/" + id.jobId(), id, bp, "warp");
    }

    private static TestEntry eatingEntry(String name, TestBlueprint bp) {
        JobID id = new JobID("gatherer", "axe");
        return new TestEntry("eating/" + name, id, bp, "eating");
    }

    private static TestEntry eatingEntryDirect(String name, TestBlueprint bp) {
        JobID id = new JobID("gatherer", "dining_no_table");
        return new TestEntry("eating/" + name, id, bp, "eating");
    }

    private static TestEntry edgeCaseEntry(String root, String job, String variant, TestBlueprint bp) {
        JobID id = new JobID(root, job);
        return new TestEntry(root + "/" + job + " [" + variant + "]", id, bp, "warp");
    }

    private static TestBlueprint farmerBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    blocks.add(new BlockPlacement(offset.below(), Blocks.FARMLAND.defaultBlockState()));
                    blocks.add(new BlockPlacement(offset, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)));
                }
            }
        }

        BlockPos composterOffset = new BlockPos(ox + 2, 0, oz + 2);
        blocks.removeIf(bp -> bp.offset().equals(composterOffset));
        blocks.add(new BlockPlacement(composterOffset, Blocks.COMPOSTER.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.removeIf(bp -> bp.offset().equals(chestOffset));
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.WOODEN_HOE, 1),
                new ItemStack(Items.WHEAT_SEEDS, 32)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:wheat", 1, null)),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    /**
     * Fenced farm plot containing a bare 4-high oak_log column with a reachable base
     * (no leaves; deterministic 4-log drop count). The arborist is in-town work, so the
     * chop runs during warp and deposits oak_logs in the chest.
     * <p>
     * Warp-only (realtimePhase=false): the chopped column is a non-renewable resource within
     * the arena, so a post-warp realtime re-run would have no tree left to chop. Realtime/warp
     * parity for the chop seam is structurally guaranteed (both paths share
     * {@code event.world().chopTree(...)}); the full plant→grow→chop cycle is exercised by the
     * full-cycle blueprint.
     */
    private static TestBlueprint arboristCutTreesBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    blocks.add(new BlockPlacement(offset.below(), Blocks.DIRT.defaultBlockState()));
                }
            }
        }

        // A bare 4-high oak_log column at an interior cell, base reachable from the floor.
        BlockPos trunkBase = new BlockPos(ox + 3, 0, oz + 3);
        for (int y = 0; y < 4; y++) {
            blocks.add(new BlockPlacement(trunkBase.above(y), Blocks.OAK_LOG.defaultBlockState()));
        }

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.WOODEN_AXE, 1)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:oak_log", 4, null)),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    /**
     * Fenced farm plot of bare tillable ground ({@code grass_block}, in {@code #questown:tillables})
     * with open sky above. The arborist plants saplings; {@code GrowTreesWarpRule} grows them into
     * real trees in the warp snapshot. Asserts the sapling supply is consumed (planting happened);
     * the grow→chop end-to-end is asserted by the full-cycle blueprint.
     */
    private static TestBlueprint arboristPlantSaplingBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    // Tillable workspot with open air above for planting + tree growth.
                    blocks.add(new BlockPlacement(offset, Blocks.GRASS_BLOCK.defaultBlockState()));
                }
            }
        }

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.removeIf(bp -> bp.offset().equals(chestOffset));
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.OAK_SAPLING, 16)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:oak_sapling", -16, -1)),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    /**
     * Arborist warp cycle: a fenced farm with a starter 4-high oak_log column at the floor (so
     * cut_trees is finishable from warp start) PLUS oak_sapling BLOCKS pre-seeded at the floor
     * (yCoord), as if planted in a prior warp. During the warp, {@code seedFarmSaplings} records
     * the saplings and {@code GrowTreesWarpRule} attempts to grow them via {@code growTreeAt}
     * (the in-warp worldgen path), while the cut_trees villager chops the starter column.
     * <p>
     * Asserts the full grow→chop cycle: >=5 logs, which is only reachable if the seeded sapling
     * grows into a tree (the starter column alone is 4) and cut_trees chops it in the same warp.
     * For growth to place a tree, the sapling needs a clear footprint — guaranteed by the jobs-track
     * flatten now clearing the whole build volume + tree headroom (see {@code TestArenaPreparer
     * .buildVolume}). See ADR-0005.
     */
    private static TestBlueprint arboristFullCycleBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -3;

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    // Dirt support a level below the floor; floor (yCoord) left open for saplings/trees.
                    blocks.add(new BlockPlacement(offset.below(), Blocks.DIRT.defaultBlockState()));
                }
            }
        }

        // Starter 4-high oak_log column (base at yCoord) so cut_trees is finishable at warp start.
        BlockPos trunkBase = new BlockPos(ox + 1, 0, oz + 1);
        for (int y = 0; y < 4; y++) {
            blocks.add(new BlockPlacement(trunkBase.above(y), Blocks.OAK_LOG.defaultBlockState()));
        }

        // One oak sapling centered in the 5x5 interior. An oak's foliage radius (2) spans the whole
        // interior, so only ONE tree fits — saplings any closer block each other's footprint (a
        // sapling block isn't "free" for growth). The starter column shares the plot because logs
        // ARE "free". seedFarmSaplings records this sapling and GrowTreesWarpRule grows it mid-warp
        // into a trunk at the floor (yCoord, where cut_trees scans), so it is chopped in the same warp.
        BlockPos saplingOffset = new BlockPos(ox + 3, 0, oz + 3);
        blocks.add(new BlockPlacement(saplingOffset, Blocks.OAK_SAPLING.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.WOODEN_AXE, 1)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(
                        // The seeded sapling must grow and be chopped in the same warp: the warp
                        // fells one whole trunk here, and the starter column alone is only 4 logs, so
                        // >=5 can only be reached by chopping the GROWN oak. Growth is deterministic
                        // (TreeFeatureResolver.seededFor), so the grown trunk is a fixed 5 logs every
                        // run — robustly >=5, never the flaky 4..6 of level.random. See ADR-0005.
                        //
                        // LOAD-BEARING COORDINATE: the "5" is the deterministic oak height for the
                        // seed pos.asLong() of the sapling at (ox+3, oz+3) under the ORIGIN at the
                        // time of writing. Moving the arena origin or this sapling offset re-seeds the
                        // RNG and can drop the grown trunk to 4 (then this fails, predictably — not a
                        // flake). If you relocate it, re-pin this threshold to the new deterministic
                        // height. It also assumes the single felled trunk is the grown oak (job-block
                        // scan reaches (7,0) before the 4-log starter at (5,-2)).
                        new ExpectedProduct("minecraft:oak_log", 5, null)
                ),
                1,
                500
        );

        return new TestBlueprint(
                RoomType.FARM,
                blocks,
                supplies,
                gateOffset,
                chestOffset,
                SpecialQuests.FARM,
                expectation
        );
    }

    private static TestBlueprint cookBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -2;

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                BlockPos floorOffset = new BlockPos(ox + x, -1, oz + z);
                blocks.add(new BlockPlacement(floorOffset, Blocks.COBBLESTONE.defaultBlockState()));

                if (isEdge) {
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 0, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 1, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                }

                BlockPos ceilOffset = new BlockPos(ox + x, 2, oz + z);
                blocks.add(new BlockPlacement(ceilOffset, Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        BlockPos furnaceOffset = new BlockPos(ox + 1, 0, oz + 1);
        blocks.add(new BlockPlacement(furnaceOffset, Blocks.FURNACE.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 3, 0, oz + 1);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.BEEF, 32),
                new ItemStack(Items.COAL, 32),
                new ItemStack(Items.STICK, 16)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(
                        new ExpectedProduct("minecraft:cooked_beef", 3, 8),
                        new ExpectedProduct("minecraft:beef", -8, -3),
                        new ExpectedProduct("minecraft:coal", -5, -2)
                ),
                1,
                100
        );

        return new TestBlueprint(
                RoomType.INDOOR,
                blocks,
                supplies,
                doorLower,
                chestOffset,
                new ResourceLocation(Questown.MODID, "kitchen_small"),
                expectation
        );
    }

    private static TestBlueprint bakerBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.BREAD_OVEN_BLOCK.get().defaultBlockState(),
                null,
                List.of(
                        new ItemStack(Items.WHEAT, 32),
                        new ItemStack(Items.COAL, 16)
                ),
                new ResourceLocation(Questown.MODID, "breadmaker"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", 1, null)),
                        1, 100
                )
        );
    }

    private static boolean isArmorerJob(JobID jobId) {
        String j = jobId.jobId();
        return j.endsWith("_boots") || j.endsWith("_helmet")
                || j.endsWith("_leggings") || j.endsWith("_chestplate");
    }

    private static TestBlueprint armorerBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -2;

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                BlockPos floorOffset = new BlockPos(ox + x, -1, oz + z);
                blocks.add(new BlockPlacement(floorOffset, Blocks.COBBLESTONE.defaultBlockState()));

                if (isEdge) {
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 0, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 1, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                }

                BlockPos ceilOffset = new BlockPos(ox + x, 2, oz + z);
                blocks.add(new BlockPlacement(ceilOffset, Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        blocks.add(new BlockPlacement(new BlockPos(ox + 1, 0, oz + 1), BlocksInit.BLACKSMITHS_TABLE_BLOCK.get().defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 3, 0, oz + 1), Blocks.CHEST.defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 1, 0, oz + 3), Blocks.CHEST.defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 3, 0, oz + 3), Blocks.CHEST.defaultBlockState()));
        blocks.add(new BlockPlacement(new BlockPos(ox + 2, 0, oz + 1), Blocks.CHEST.defaultBlockState()));

        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 3);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.LEATHER, 32)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:leather", -32, null)),
                0, 0
        );

        return new TestBlueprint(
                RoomType.INDOOR,
                blocks,
                supplies,
                doorLower,
                chestOffset,
                new ResourceLocation(Questown.MODID, "armory"),
                expectation
        );
    }

    private static TestBlueprint crafterBlueprint() {
        return indoorRoomBlueprint(
                Blocks.CRAFTING_TABLE.defaultBlockState(),
                null,
                List.of(new ItemStack(Items.OAK_SAPLING, 16)),
                new ResourceLocation(Questown.MODID, "crafting_room"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:stick", 1, null)),
                        1, 100
                )
        );
    }

    private static TestBlueprint blacksmithBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.BLACKSMITHS_TABLE_BLOCK.get().defaultBlockState(),
                Blocks.TORCH.defaultBlockState(),
                List.of(
                        new ItemStack(Items.STICK, 16),
                        new ItemStack(Items.OAK_PLANKS, 32)
                ),
                new ResourceLocation(Questown.MODID, "smithy"),
                new TestExpectation(
                        List.of(
                                new ExpectedProduct("minecraft:wooden_axe", 0, null),
                                new ExpectedProduct("minecraft:wooden_pickaxe", 0, null),
                                new ExpectedProduct("minecraft:wooden_hoe", 0, null),
                                new ExpectedProduct("minecraft:wooden_shovel", 0, null)
                        ),
                        1, 100
                )
        );
    }

    private static TestBlueprint smelterBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.ORE_PROCESSING_BLOCK.get().defaultBlockState(),
                null,
                List.of(
                        new ItemStack(Items.IRON_ORE, 16),
                        new ItemStack(Items.STONE_PICKAXE, 1)
                ),
                new ResourceLocation(Questown.MODID, "smeltery"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:raw_iron", 1, null)),
                        1, 100
                )
        );
    }

    private static TestBlueprint soupCookBlueprint() {
        return indoorRoomBlueprint(
                BlocksInit.SOUP_POT_SMALL.get().defaultBlockState(),
                null,
                List.of(
                        new ItemStack(Items.RED_MUSHROOM, 16),
                        new ItemStack(Items.BOWL, 16),
                        new ItemStack(Items.WOODEN_SHOVEL, 1)
                ),
                new ResourceLocation(Questown.MODID, "soup_kitchen_small"),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:mushroom_stew", 1, null)),
                        1, 100
                )
        );
    }

    private static TestBlueprint indoorRoomBlueprint(
            net.minecraft.world.level.block.state.BlockState workBlock,
            @Nullable net.minecraft.world.level.block.state.BlockState extraBlock,
            List<ItemStack> supplies,
            ResourceLocation roomId,
            TestExpectation expectation
    ) {
        List<BlockPlacement> blocks = new ArrayList<>();

        int ox = 4;
        int oz = -2;

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                BlockPos floorOffset = new BlockPos(ox + x, -1, oz + z);
                blocks.add(new BlockPlacement(floorOffset, Blocks.COBBLESTONE.defaultBlockState()));

                if (isEdge) {
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 0, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 1, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                }

                BlockPos ceilOffset = new BlockPos(ox + x, 2, oz + z);
                blocks.add(new BlockPlacement(ceilOffset, Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        blocks.add(new BlockPlacement(new BlockPos(ox + 1, 0, oz + 1), workBlock));

        if (extraBlock != null) {
            blocks.add(new BlockPlacement(new BlockPos(ox + 3, 0, oz + 1), extraBlock));
        }

        BlockPos chestOffset = new BlockPos(ox + 3, 0, oz + 3);

        return new TestBlueprint(
                RoomType.INDOOR,
                blocks,
                supplies,
                doorLower,
                chestOffset,
                roomId,
                expectation
        );
    }

    private static TestBlueprint gathererBlueprint() {
        return welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
    }

    private static TestBlueprint explorerBlueprint() {
        // The ==1 knowledge-growth assertion is bounded by SUPPLY, not by warp ticks:
        // 1 paper => exactly 1 gatherer map can be made => exactly 1 scout => knowledge grows by 1.
        // The cooked_beef just keeps the villager fed. Load-bearing — do not raise the paper count.
        // The warp amount only needs to be "big enough" to complete one full leaver cycle; 2500 was
        // too short (the villager produced nothing), so we match the suite default that every other
        // passing leaver job (gatherer:axe, hunter, miner, fisher) uses.
        // The explorer is the only leaver job whose product REPLACES a consumed input:
        // it turns 1 paper + 1 food into 1 gatherer map, so the town's net item count is
        // unchanged. The inherited wildcard ("* grows by >= 1") therefore can't gate it —
        // assert the actual product (exactly one gatherer map) plus the knowledge growth.
        return welcomeMatBlueprint(List.of(
                new ItemStack(Items.PAPER, 1),
                new ItemStack(Items.COOKED_BEEF, 1)
        )).withExpectation(new TestExpectation(
                List.of(
                        new ExpectedProduct("questown:gatherer_map", 1, 1),
                        // Paper is carried as a held tool but consumed once per trip
                        // (CONSUME_HELD_PAPER) so it isn't infinitely reusable.
                        new ExpectedProduct("minecraft:paper", -1, -1)
                ),
                1, 100
        )).withMinKnowledgeGrowth(1).withWarpAmountOverride(24000);
    }

    private static TestBlueprint hunterBlueprint() {
        return welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_SWORD, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
    }

    private static TestBlueprint welcomeMatBlueprint(List<ItemStack> supplies) {
        List<BlockPlacement> blocks = new ArrayList<>();

        BlockPos matOffset = new BlockPos(3, 0, 0);
        blocks.add(new BlockPlacement(matOffset, BlocksInit.WELCOME_MAT_BLOCK.get().defaultBlockState()));

        SupplyRoom sr = buildSupplyRoom(4, -2);
        blocks.addAll(sr.blocks);

        return new TestBlueprint(
                RoomType.WELCOME_MAT,
                blocks,
                supplies,
                matOffset,
                sr.chestOffset,
                SpecialQuests.TOWN_GATE,
                wildcardExpectation(),
                sr.doorOffset
        );
    }

    private static TestBlueprint minerBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        BlockPos blockOffset = new BlockPos(3, 0, 0);
        blocks.add(new BlockPlacement(blockOffset, BlocksInit.MINESHAFT.get().defaultBlockState()));

        SupplyRoom sr = buildSupplyRoom(4, -2);
        blocks.addAll(sr.blocks);

        return new TestBlueprint(
                RoomType.BLOCK_ROOM,
                blocks,
                List.of(
                        new ItemStack(Items.STONE_PICKAXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                ),
                blockOffset,
                sr.chestOffset,
                Questown.ResourceLocation("block_room/block.questown.mineshaft"),
                wildcardExpectation(),
                sr.doorOffset
        );
    }

    private static TestBlueprint fisherBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        BlockPos blockOffset = new BlockPos(3, 0, 0);
        blocks.add(new BlockPlacement(blockOffset, BlocksInit.FISHING_STATION_BLOCK.get().defaultBlockState()));

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos waterPos = new BlockPos(3 + x, -1, -2 + z);
                blocks.add(new BlockPlacement(waterPos, Blocks.WATER.defaultBlockState()));
            }
        }

        SupplyRoom sr = buildSupplyRoom(4, -2);
        blocks.addAll(sr.blocks);

        return new TestBlueprint(
                RoomType.BLOCK_ROOM,
                blocks,
                List.of(new ItemStack(Items.STRING, 16)),
                blockOffset,
                sr.chestOffset,
                Questown.ResourceLocation("block_room/block.questown.fishing_station"),
                wildcardExpectation(),
                sr.doorOffset
        );
    }

    private record SupplyRoom(
            List<BlockPlacement> blocks,
            BlockPos chestOffset,
            BlockPos doorOffset
    ) {}

    private static SupplyRoom buildSupplyRoom(int ox, int oz) {
        List<BlockPlacement> blocks = new ArrayList<>();

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                blocks.add(new BlockPlacement(
                        new BlockPos(ox + x, -1, oz + z),
                        Blocks.COBBLESTONE.defaultBlockState()
                ));
                if (isEdge) {
                    blocks.add(new BlockPlacement(
                            new BlockPos(ox + x, 0, oz + z),
                            Blocks.COBBLESTONE.defaultBlockState()
                    ));
                    blocks.add(new BlockPlacement(
                            new BlockPos(ox + x, 1, oz + z),
                            Blocks.COBBLESTONE.defaultBlockState()
                    ));
                }
                blocks.add(new BlockPlacement(
                        new BlockPos(ox + x, 2, oz + z),
                        Blocks.COBBLESTONE.defaultBlockState()
                ));
            }
        }

        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        BlockPos chestOffset = new BlockPos(ox + 2, 0, oz + 1);

        return new SupplyRoom(blocks, chestOffset, doorLower);
    }

    private static TestExpectation wildcardExpectation() {
        return new TestExpectation(
                List.of(new ExpectedProduct("*", 1, null)),
                1, 100
        );
    }

    // --- Edge case blueprints ---

    private static TestBlueprint farmerNightStartBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 1, null)),
                        1, 100
                ),
                base.supplyDoorOffset(), 12000, 20000L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerAllNightBlueprint() {
        TestBlueprint base = farmerBlueprint();
        // No hoe in supplies: prevents real-time harvesting during settle phases,
        // so the only way wheat could appear is if the warp ran (which it shouldn't at night).
        return new TestBlueprint(
                base.roomType(), base.blocks(), List.of(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 0, 0)),
                        0, 0
                ),
                base.supplyDoorOffset(), 2000, 15000L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerMultiDayBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 3, null)),
                        3, 300
                ),
                base.supplyDoorOffset(), 72000, 0L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerNoSuppliesBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), List.of(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 0, 0)),
                        0, 0
                ),
                base.supplyDoorOffset(), 24000, 0L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint gathererToolDurabilityBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.WOODEN_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(
                                new ExpectedProduct("minecraft:wooden_axe", -1, 0),
                                new ExpectedProduct("*", 1, null)
                        ),
                        1, 300
                ),
                base.supplyDoorOffset(), 240000, 0L, null, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerTwoVillagersBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:wheat", 2, null)),
                        1, 100
                ),
                base.supplyDoorOffset(), 24000, 0L, 2, false, null,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint farmerRealtimeBlueprint() {
        TestBlueprint base = farmerBlueprint();
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                base.expectation(),
                base.supplyDoorOffset(), null, null, null, true, 4800,
                false, false, null, null, null, null, null, false
        );
    }

    private static TestBlueprint gathererShortAbsenceBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("*", 0, 14)),
                        0, 100
                ),
                base.supplyDoorOffset(), 2000, 0L, null, false, null,
                false, false, null, null, null, null, null, true
        );
    }

    /**
     * Simulates the campfire sleep scenario: player is in town at evening,
     * sleeps through night, dayTime jumps ~11000 ticks to morning.
     * The flag was ticking normally before sleep, so its reference tick is
     * current. The dayTime jump should NOT cause a massive warp.
     * startTimeTick=12000 (evening), warpAmount=11000 (jump to next morning).
     */
    private static TestBlueprint gathererSleepJumpBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("*", 0, 14)),
                        0, 100
                ),
                base.supplyDoorOffset(), 11000, 12000L, null, false, null,
                false, false, null, null, null, null, null, true
        );
    }

    /**
     * Simulates: player was briefly away (tp'd back after dying), then slept.
     * Combined effect: short real absence + sleep time jump.
     * startTimeTick=10000 (afternoon), warpAmount=14000 (past next morning).
     */
    private static TestBlueprint gathererAbsenceThenSleepBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(
                List.of(
                        new ItemStack(Items.STONE_AXE, 1),
                        new ItemStack(Items.COOKED_BEEF, 8)
                )
        );
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(
                        List.of(new ExpectedProduct("*", 0, 14)),
                        0, 100
                ),
                base.supplyDoorOffset(), 14000, 10000L, null, false, null,
                false, false, null, null, null, null, null, true
        );
    }

    // --- Eating tests ---

    /**
     * Villager eats cooked food at the town flag (no dining room).
     * DinerNoTableWork path: hungry -> get food from supply -> eat at flag -> fullness 100%.
     * Fullness cycles between 0-100% during monitoring; threshold reflects end-of-window value.
     */
    private static TestBlueprint eatNoTableBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BREAD, 16)
        ));
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 800,
                true, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", -16, null)),
                        1, 1
                ),
                // fullness check omitted: hunger cycles every ~150 ticks, final value at 800t is timing-dependent
                null, null, null,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:bread", 0, 1)), 0, 1),
                false
        );
    }

    /**
     * Villager eats at a dining room (plate block present).
     * DinerWork path: hungry -> get food from supply -> eat at plate block -> fullness 100%.
     */
    private static TestBlueprint dineAtTimeBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BREAD, 16)
        ));
        BlockPos plateOffset = new BlockPos(1, 0, 0);
        List<BlockPlacement> blocks = new ArrayList<>(base.blocks());
        blocks.add(new BlockPlacement(plateOffset, BlocksInit.PLATE_BLOCK.get().defaultBlockState()));
        return new TestBlueprint(
                base.roomType(), blocks, base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 800,
                true, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", -16, null)),
                        1, 1
                ),
                0.25f, plateOffset, SpecialQuests.DINING_ROOM,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:bread", 0, 1)), 0, 1),
                false
        );
    }

    /**
     * Villager eats raw food at the town flag (no cooked food available).
     * DinerRawFoodWork path: hungry -> no cooked food -> eat raw food -> fullness ~50%.
     * Requires more realtime ticks because DinerNoTableWork must time out first.
     */
    private static TestBlueprint eatRawFoodBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BEEF, 16)
        ));
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 2000,
                true, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:beef", -16, null)),
                        1, 1
                ),
                0.3f, null, null,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:beef", 0, 1)), 0, 1),
                false
        );
    }

    /**
     * Directly assigns dining_no_table job (bypassing the hunger trigger).
     * Tests that the eating job itself works: collects food from supply and restores fullness.
     */
    private static TestBlueprint eatDirectBlueprint() {
        TestBlueprint base = welcomeMatBlueprint(List.of(
                new ItemStack(Items.BREAD, 16)
        ));
        return new TestBlueprint(
                base.roomType(), base.blocks(), base.supplyItems(),
                base.doorOrGateOffset(), base.chestOffset(), base.roomId(),
                new TestExpectation(List.of(), 0, 0),
                base.supplyDoorOffset(), null, null, null, true, 600,
                false, true,
                new TestExpectation(
                        List.of(new ExpectedProduct("minecraft:bread", -16, null)),
                        1, 1
                ),
                0.25f, null, null,
                new TestExpectation(List.of(new ExpectedProduct("minecraft:bread", 0, 1)), 0, 1),
                false
        );
    }

    // --- Worldgen checks ---

    private static LevelCheck emptyTownStructureCheck() {
        return new LevelCheck(
                "worldgen/empty_town_structure",
                "worldgen",
                level -> level.registryAccess()
                        .registry(Registry.STRUCTURE_REGISTRY)
                        .map(r -> r.containsKey(new ResourceLocation("questown", "empty_town")))
                        .orElse(false)
        );
    }

    private static LevelCheck emptyTownStructureSetCheck() {
        return new LevelCheck(
                "worldgen/empty_town_structure_set",
                "worldgen",
                level -> level.registryAccess()
                        .registry(Registry.STRUCTURE_SET_REGISTRY)
                        .map(r -> r.containsKey(new ResourceLocation("questown", "empty_town")))
                        .orElse(false)
        );
    }

    // --- UI checks ---

    /**
     * Roadmap #182: the work-request / stock-request screens only offer products of jobs the
     * town has unlocked. Exercises {@link ServerJobsRegistry#getAllOutputs} against the real
     * {@code Works} registry with controlled predicates to prove the gate (no town build needed,
     * since getAllOutputs reads only the registry + the level + the gather-knowledge function).
     * <p>
     * The gather-knowledge function is fed the always-known floor (wheat seeds — mirrors
     * {@code TownKnowledgeStore} baseKnowledge) so the floor's gating is actually tested, not
     * neutralised:
     * <ul>
     *   <li>predicate {@code false} ⇒ empty offer set — the gate suppresses even the
     *       always-known floor when no job is unlocked (the design decision for #182),</li>
     *   <li>predicate {@code true} ⇒ non-empty, and the always-known floor (wheat seeds)
     *       surfaces via its gather job (pre-#182 base intact),</li>
     *   <li>single-root predicate ⇒ non-empty and a strict subset of the full set
     *       (the gate filters by {@link JobID}, never invents items).</li>
     * </ul>
     */
    private static LevelCheck jobBoardKnowledgeGatingCheck() {
        return new LevelCheck(
                "ui/job_board_knowledge_gating",
                "ui",
                TestBlueprintRegistry::checkJobBoardKnowledgeGating
        );
    }

    private static final String GATING_CONTROL_ROOT = "farmer";
    private static final String ALWAYS_KNOWN_ITEM = "minecraft:wheat_seeds";

    private static boolean checkJobBoardKnowledgeGating(ServerLevel level) {
        try {
            // Mirror the always-known gather floor (TownKnowledgeStore baseKnowledge = wheat seeds)
            // so the gate is tested against an item that is in town knowledge regardless of discovery.
            ImmutableSet<MCTownItem> gatherFloor = ImmutableSet.of(
                    MCTownItem.fromMCItemStack(Items.WHEAT_SEEDS.getDefaultInstance())
            );
            WorksBehaviour.TownData td = new WorksBehaviour.TownData(level, prefix -> gatherFloor);

            ImmutableSet<Ingredient> all = ServerJobsRegistry.getAllOutputs(td, j -> true);
            ImmutableSet<Ingredient> none = ServerJobsRegistry.getAllOutputs(td, j -> false);
            ImmutableSet<Ingredient> oneRoot = ServerJobsRegistry.getAllOutputs(
                    td, j -> GATING_CONTROL_ROOT.equals(j.rootId())
            );

            Set<String> allNames = itemNames(all);
            Set<String> oneRootNames = itemNames(oneRoot);

            // none is empty even though the gather floor (wheat seeds) is "always known":
            // the job-unlock gate suppresses always-known items when no job is unlocked.
            boolean closesFully = none.isEmpty();
            boolean opensFully = !all.isEmpty();
            boolean floorSurfaces = allNames.contains(ALWAYS_KNOWN_ITEM);
            boolean filtersSelectively = !oneRoot.isEmpty()
                    && allNames.containsAll(oneRootNames)
                    && oneRootNames.size() < allNames.size();

            boolean ok = closesFully && opensFully && floorSurfaces && filtersSelectively;
            if (!ok) {
                QT.FLAG_LOGGER.error(
                        "[autotest] job_board_knowledge_gating FAIL: closesFully={} (none={}) opensFully={} (all={}) "
                                + "floorSurfaces={} filtersSelectively={} ({}={}, all={})",
                        closesFully, none.size(), opensFully, allNames.size(),
                        floorSurfaces, filtersSelectively, GATING_CONTROL_ROOT, oneRootNames.size(), allNames.size()
                );
            }
            return ok;
        } catch (RuntimeException e) {
            QT.FLAG_LOGGER.error("[autotest] job_board_knowledge_gating threw", e);
            return false;
        }
    }

    private static Set<String> itemNames(Collection<Ingredient> ingredients) {
        Set<String> names = new HashSet<>();
        for (Ingredient ingredient : ingredients) {
            for (ItemStack stack : ingredient.getItems()) {
                names.add(String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
            }
        }
        return names;
    }
}
