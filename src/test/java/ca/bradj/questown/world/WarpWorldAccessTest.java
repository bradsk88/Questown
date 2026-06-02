package ca.bradj.questown.world;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WarpWorldAccessTest {

    private static final BlockPos FURNACE = new BlockPos(0, 64, 0);
    private static final BlockPos FIELD = new BlockPos(5, 64, 5);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static WarpWorldAccess furnaceWorld(ItemStack ingredient, ItemStack fuel, ItemStack output) {
        List<ItemStack> slots = new ArrayList<>(Arrays.asList(ingredient.copy(), fuel.copy(), output.copy()));
        Map<BlockPos, List<ItemStack>> containers = new HashMap<>();
        containers.put(FURNACE, slots);
        return new WarpWorldAccess(
                new HashMap<>(),
                containers,
                item -> item.is(Items.BEEF)
                        ? Optional.of(new WarpWorldAccess.SmeltResult(new ItemStack(Items.COOKED_BEEF), 200))
                        : Optional.empty(),
                item -> item.is(Items.COAL) ? 1600 : 0
        );
    }

    private static WarpWorldAccess emptyFurnaceWorld() {
        return furnaceWorld(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
    }

    // -------------------------------------------------------------------------
    // advanceProcessing — basic production
    // -------------------------------------------------------------------------

    @Test
    void advanceProcessing_producesOutput_afterEnoughTicks() {
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.BEEF), new ItemStack(Items.COAL), ItemStack.EMPTY
        );
        world.advanceProcessing(FURNACE, 300);

        ItemStack out = world.getContainerSlot(FURNACE, 2);
        assertEquals(Items.COOKED_BEEF, out.getItem());
        assertEquals(1, out.getCount());
        assertTrue(world.dirtyContainers.contains(FURNACE));
    }

    @Test
    void advanceProcessing_consumesIngredient() {
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.BEEF, 3), new ItemStack(Items.COAL), ItemStack.EMPTY
        );
        world.advanceProcessing(FURNACE, 300);

        // One beef consumed
        assertEquals(2, world.getContainerSlot(FURNACE, 0).getCount());
    }

    // -------------------------------------------------------------------------
    // advanceProcessing — output slot full
    // -------------------------------------------------------------------------

    @Test
    void advanceProcessing_stopsWhenOutputFull() {
        ItemStack fullOutput = new ItemStack(Items.COOKED_BEEF, 64);
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.BEEF, 8), new ItemStack(Items.COAL, 8), fullOutput
        );
        world.advanceProcessing(FURNACE, 5000);

        // Output slot must stay at max, no overflow
        assertEquals(64, world.getContainerSlot(FURNACE, 2).getCount());
        // Input should be unchanged (couldn't produce)
        assertEquals(8, world.getContainerSlot(FURNACE, 0).getCount());
    }

    // -------------------------------------------------------------------------
    // advanceProcessing — no fuel
    // -------------------------------------------------------------------------

    @Test
    void advanceProcessing_doesNothing_whenNoFuel() {
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.BEEF), ItemStack.EMPTY, ItemStack.EMPTY
        );
        world.advanceProcessing(FURNACE, 1000);

        assertTrue(world.getContainerSlot(FURNACE, 2).isEmpty());
        // Ingredient should be untouched
        assertFalse(world.getContainerSlot(FURNACE, 0).isEmpty());
    }

    // -------------------------------------------------------------------------
    // advanceProcessing — no ingredient
    // -------------------------------------------------------------------------

    @Test
    void advanceProcessing_doesNothing_whenNoIngredient() {
        WarpWorldAccess world = furnaceWorld(
                ItemStack.EMPTY, new ItemStack(Items.COAL), ItemStack.EMPTY
        );
        world.advanceProcessing(FURNACE, 1000);

        assertTrue(world.getContainerSlot(FURNACE, 2).isEmpty());
        assertFalse(world.dirtyContainers.contains(FURNACE));
    }

    // -------------------------------------------------------------------------
    // advanceProcessing — cook progress accumulates across calls
    // -------------------------------------------------------------------------

    @Test
    void advanceProcessing_accumulatesProgressAcrossCalls() {
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.BEEF), new ItemStack(Items.COAL), ItemStack.EMPTY
        );
        // 100 ticks — not enough to finish (needs 200)
        world.advanceProcessing(FURNACE, 100);
        assertTrue(world.getContainerSlot(FURNACE, 2).isEmpty(), "Should not produce after 100 ticks");

        // Another 100 ticks — now 200 total, should complete
        world.advanceProcessing(FURNACE, 100);
        assertFalse(world.getContainerSlot(FURNACE, 2).isEmpty(), "Should produce after 200 ticks total");
        assertEquals(Items.COOKED_BEEF, world.getContainerSlot(FURNACE, 2).getItem());
    }

    @Test
    void advanceProcessing_progressResetsAfterProduction() {
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.BEEF, 2), new ItemStack(Items.COAL, 64), ItemStack.EMPTY
        );
        // Cook both items, 250 ticks between them
        world.advanceProcessing(FURNACE, 250);
        world.advanceProcessing(FURNACE, 250);

        // Both items should be cooked
        assertEquals(2, world.getContainerSlot(FURNACE, 2).getCount());
        assertTrue(world.getContainerSlot(FURNACE, 0).isEmpty());
    }

    // -------------------------------------------------------------------------
    // advanceProcessing — unrecognised ingredient
    // -------------------------------------------------------------------------

    @Test
    void advanceProcessing_doesNothing_forUnrecognisedIngredient() {
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.DIRT), new ItemStack(Items.COAL), ItemStack.EMPTY
        );
        world.advanceProcessing(FURNACE, 1000);
        assertTrue(world.getContainerSlot(FURNACE, 2).isEmpty());
    }

    // -------------------------------------------------------------------------
    // advanceProcessing — position without container
    // -------------------------------------------------------------------------

    @Test
    void advanceProcessing_isNoOp_forUnknownPosition() {
        WarpWorldAccess world = emptyFurnaceWorld();
        // Should not throw
        world.advanceProcessing(new BlockPos(99, 64, 99), 1000);
    }

    // -------------------------------------------------------------------------
    // Container slot operations
    // -------------------------------------------------------------------------

    @Test
    void insertIntoSlot_succeedsWhenEmpty() {
        WarpWorldAccess world = emptyFurnaceWorld();
        boolean inserted = world.insertIntoSlot(FURNACE, 0, new ItemStack(Items.BEEF));
        assertTrue(inserted);
        assertEquals(Items.BEEF, world.getContainerSlot(FURNACE, 0).getItem());
        assertTrue(world.dirtyContainers.contains(FURNACE));
    }

    @Test
    void insertIntoSlot_failsWhenOccupied() {
        WarpWorldAccess world = furnaceWorld(
                new ItemStack(Items.BEEF), ItemStack.EMPTY, ItemStack.EMPTY
        );
        boolean inserted = world.insertIntoSlot(FURNACE, 0, new ItemStack(Items.PORKCHOP));
        assertFalse(inserted);
        // Original content unchanged
        assertEquals(Items.BEEF, world.getContainerSlot(FURNACE, 0).getItem());
    }

    @Test
    void extractFromSlot_returnsEmptyForEmptySlot() {
        WarpWorldAccess world = emptyFurnaceWorld();
        ItemStack extracted = world.extractFromSlot(FURNACE, 2, 1);
        assertTrue(extracted.isEmpty());
    }

    @Test
    void extractFromSlot_reducesCount() {
        WarpWorldAccess world = furnaceWorld(
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.COOKED_BEEF, 3)
        );
        ItemStack extracted = world.extractFromSlot(FURNACE, 2, 2);
        assertEquals(2, extracted.getCount());
        assertEquals(1, world.getContainerSlot(FURNACE, 2).getCount());
        assertTrue(world.dirtyContainers.contains(FURNACE));
    }

    @Test
    void extractFromSlot_clearsSlotWhenCountExhausted() {
        WarpWorldAccess world = furnaceWorld(
                ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.COOKED_BEEF)
        );
        world.extractFromSlot(FURNACE, 2, 1);
        assertTrue(world.getContainerSlot(FURNACE, 2).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Block property operations and dirty tracking
    // -------------------------------------------------------------------------

    @Test
    void setBlockIntProperty_marksDirty() {
        BlockState wheatAge5 = Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 5);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(FIELD, wheatAge5);

        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);

        world.setBlockIntProperty(FIELD, "age", 7);
        assertTrue(world.dirtyBlocks.contains(FIELD));
        assertEquals(OptionalInt.of(7), world.getBlockIntProperty(FIELD, "age"));
    }

    @Test
    void setBlockIntProperty_doesNothingForUnknownProperty() {
        BlockState state = Blocks.WHEAT.defaultBlockState();
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(FIELD, state);

        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);
        world.setBlockIntProperty(FIELD, "nonexistent", 3);
        assertFalse(world.dirtyBlocks.contains(FIELD));
    }

    @Test
    void getBlockIntProperty_returnsEmptyForUnknownProperty() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(FIELD, Blocks.WHEAT.defaultBlockState());
        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);

        assertEquals(OptionalInt.empty(), world.getBlockIntProperty(FIELD, "nonexistent"));
    }

    @Test
    void removeBlock_marksDirtyAsAir() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(FIELD, Blocks.WHEAT.defaultBlockState());
        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);

        world.removeBlock(FIELD);
        assertTrue(world.dirtyBlocks.contains(FIELD));
        assertTrue(world.getBlockIntProperty(FIELD, "age").isEmpty()); // air has no age
    }

    // -------------------------------------------------------------------------
    // chopTree
    // -------------------------------------------------------------------------

    @Test
    void chopTree_removesConnectedBlocks_andReturnsDrops() {
        // 3-block vertical trunk: base, mid, top
        BlockPos base = new BlockPos(0, 64, 0);
        BlockPos mid  = new BlockPos(0, 65, 0);
        BlockPos top  = new BlockPos(0, 66, 0);
        BlockState log = Blocks.OAK_LOG.defaultBlockState();

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(base, log);
        blocks.put(mid, log);
        blocks.put(top, log);

        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);
        List<ItemStack> drops = world.chopTree(base);

        assertEquals(3, drops.size());
        assertTrue(drops.stream().allMatch(s -> s.is(Items.OAK_LOG)));

        assertTrue(world.dirtyBlocks.contains(base));
        assertTrue(world.dirtyBlocks.contains(mid));
        assertTrue(world.dirtyBlocks.contains(top));

        // All positions should now be AIR in the in-memory state
        assertTrue(world.isAir(base));
        assertTrue(world.isAir(mid));
        assertTrue(world.isAir(top));
    }

    @Test
    void chopTree_doesNotRemoveNonMatchingAdjacentBlock() {
        BlockPos trunk = new BlockPos(0, 64, 0);
        BlockPos stone = new BlockPos(1, 64, 0);

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(trunk, Blocks.OAK_LOG.defaultBlockState());
        blocks.put(stone, Blocks.STONE.defaultBlockState());

        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);
        List<ItemStack> drops = world.chopTree(trunk);

        assertEquals(1, drops.size());
        assertFalse(world.isAir(stone));
        assertFalse(world.dirtyBlocks.contains(stone));
    }

    @Test
    void chopTree_returnsEmpty_whenTrunkPosIsAir() {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(FIELD, Blocks.AIR.defaultBlockState());

        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);
        List<ItemStack> drops = world.chopTree(FIELD);

        assertTrue(drops.isEmpty());
    }

    @Test
    void chopTree_returnsEmpty_forUnknownPosition() {
        WarpWorldAccess world = new WarpWorldAccess(
                new HashMap<>(), new HashMap<>(), i -> Optional.empty(), i -> 0
        );
        // No level, no blockStates entry — resolveBlockState returns null
        List<ItemStack> drops = world.chopTree(new BlockPos(99, 64, 99));
        assertTrue(drops.isEmpty());
    }

    @Test
    void chopTree_doesNotLoopInfinitely_onDenseCluster() {
        // 2x2x2 cube of oak logs — all connected to each other
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        for (int x = 0; x <= 1; x++) {
            for (int y = 64; y <= 65; y++) {
                for (int z = 0; z <= 1; z++) {
                    blocks.put(new BlockPos(x, y, z), log);
                }
            }
        }
        WarpWorldAccess world = new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);
        List<ItemStack> drops = world.chopTree(new BlockPos(0, 64, 0));
        assertEquals(8, drops.size());
    }

    // -------------------------------------------------------------------------
    // useItemOnBlock — in-memory planting and bone meal
    // -------------------------------------------------------------------------

    private static WarpWorldAccess worldWith(BlockPos pos, BlockState state) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(pos, state);
        return new WarpWorldAccess(blocks, new HashMap<>(), i -> Optional.empty(), i -> 0);
    }

    @Test
    void useItemOnBlock_plantsSeedBlock_atPosAbove_atAgeZero() {
        WarpWorldAccess world = worldWith(FIELD, Blocks.FARMLAND.defaultBlockState());

        boolean used = world.useItemOnBlock(new ItemStack(Items.WHEAT_SEEDS), FIELD);

        BlockPos above = FIELD.above();
        assertTrue(used);
        assertTrue(world.dirtyBlocks.contains(above));
        assertEquals(OptionalInt.of(0), world.getBlockIntProperty(above, "age"));
    }

    @Test
    void useItemOnBlock_boneMeal_advancesAgeByThree() {
        BlockState wheatAge2 = Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 2);
        WarpWorldAccess world = worldWith(FIELD, wheatAge2);

        boolean used = world.useItemOnBlock(new ItemStack(Items.BONE_MEAL), FIELD);

        assertTrue(used);
        assertTrue(world.dirtyBlocks.contains(FIELD));
        assertEquals(OptionalInt.of(5), world.getBlockIntProperty(FIELD, "age"));
    }

    @Test
    void useItemOnBlock_boneMeal_capsAtMaxAge() {
        BlockState wheatAge6 = Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 6);
        WarpWorldAccess world = worldWith(FIELD, wheatAge6);

        boolean used = world.useItemOnBlock(new ItemStack(Items.BONE_MEAL), FIELD);

        assertTrue(used);
        assertEquals(OptionalInt.of(7), world.getBlockIntProperty(FIELD, "age"));
    }

    @Test
    void useItemOnBlock_boneMeal_onMaxedCrop_isNoOp_returnsFalse() {
        BlockState wheatAge7 = Blocks.WHEAT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CropBlock.AGE, 7);
        WarpWorldAccess world = worldWith(FIELD, wheatAge7);

        boolean used = world.useItemOnBlock(new ItemStack(Items.BONE_MEAL), FIELD);

        assertFalse(used);
        assertFalse(world.dirtyBlocks.contains(FIELD));
        assertEquals(OptionalInt.of(7), world.getBlockIntProperty(FIELD, "age"));
    }

    @Test
    void useItemOnBlock_boneMeal_onNonCrop_returnsFalse() {
        WarpWorldAccess world = worldWith(FIELD, Blocks.STONE.defaultBlockState());

        boolean used = world.useItemOnBlock(new ItemStack(Items.BONE_MEAL), FIELD);

        assertFalse(used);
        assertFalse(world.dirtyBlocks.contains(FIELD));
    }

    @Test
    void useItemOnBlock_unsupportedItem_returnsFalse_withoutNpe() {
        // level == null under the test constructor — proves the punch-through is gone.
        WarpWorldAccess world = worldWith(FIELD, Blocks.FARMLAND.defaultBlockState());

        boolean used = world.useItemOnBlock(new ItemStack(Items.STICK), FIELD);

        assertFalse(used);
        assertTrue(world.dirtyBlocks.isEmpty());
    }

    @Test
    void useItemOnBlock_plantThenGrow_parity() {
        WarpWorldAccess world = worldWith(FIELD, Blocks.FARMLAND.defaultBlockState());
        BlockPos above = FIELD.above();

        world.useItemOnBlock(new ItemStack(Items.WHEAT_SEEDS), FIELD);
        // The snapshot is authoritative: a GrowCropsWarpRule-style advance sees the planted crop.
        world.setBlockIntProperty(above, "age", world.getBlockIntProperty(above, "age").getAsInt() + 1);

        assertEquals(OptionalInt.of(1), world.getBlockIntProperty(above, "age"));
    }

    // -------------------------------------------------------------------------
    // asServerLevel
    // -------------------------------------------------------------------------

    @Test
    void asServerLevel_returnsNull() {
        WarpWorldAccess world = new WarpWorldAccess(
                new HashMap<>(), new HashMap<>(), i -> Optional.empty(), i -> 0
        );
        assertNull(world.asServerLevel());
    }
}
