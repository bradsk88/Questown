package ca.bradj.questown.commands.test;

import ca.bradj.questown.Questown;
import ca.bradj.questown.commands.test.TestBlueprint.BlockPlacement;
import ca.bradj.questown.commands.test.TestBlueprint.RoomType;
import ca.bradj.questown.commands.test.TestExpectation.ExpectedProduct;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.town.special.SpecialQuests;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TestBlueprintRegistry {

    public static @Nullable TestBlueprint get(JobID jobId) {
        if ("farmer".equals(jobId.rootId())) {
            return farmerBlueprint();
        }
        if ("cook".equals(jobId.rootId())) {
            return cookBlueprint();
        }
        return null;
    }

    private static TestBlueprint farmerBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        // 7x7 fenced farm. Flag is at (0,0,0), farm starts at offset (+4,0,-3)
        int ox = 4;
        int oz = -3;

        // Oak fence perimeter
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                boolean isEdge = x == 0 || x == 6 || z == 0 || z == 6;
                BlockPos offset = new BlockPos(ox + x, 0, oz + z);
                if (isEdge) {
                    // South-side gate at center (x=3, z=6)
                    if (x == 3 && z == 6) {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE_GATE.defaultBlockState()));
                    } else {
                        blocks.add(new BlockPlacement(offset, Blocks.OAK_FENCE.defaultBlockState()));
                    }
                } else {
                    // Interior: farmland below + wheat(age=7) on top
                    blocks.add(new BlockPlacement(offset.below(), Blocks.FARMLAND.defaultBlockState()));
                    blocks.add(new BlockPlacement(offset, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7)));
                }
            }
        }

        // Replace one interior wheat with composter (at ox+2, 0, oz+2)
        BlockPos composterOffset = new BlockPos(ox + 2, 0, oz + 2);
        blocks.removeIf(bp -> bp.offset().equals(composterOffset));
        blocks.add(new BlockPlacement(composterOffset, Blocks.COMPOSTER.defaultBlockState()));

        // Chest offset inside fence (at ox+1, 0, oz+5)
        BlockPos chestOffset = new BlockPos(ox + 1, 0, oz + 5);
        blocks.removeIf(bp -> bp.offset().equals(chestOffset));
        blocks.add(new BlockPlacement(chestOffset.below(), Blocks.DIRT.defaultBlockState()));

        // Fence gate offset
        BlockPos gateOffset = new BlockPos(ox + 3, 0, oz + 6);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.WOODEN_HOE, 1),
                new ItemStack(Items.WHEAT_SEEDS, 32)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(new ExpectedProduct("minecraft:wheat", 1, -1)),
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

    private static TestBlueprint cookBlueprint() {
        List<BlockPlacement> blocks = new ArrayList<>();

        // 5x5 cobblestone room. Flag at (0,0,0), room starts at offset (+4,0,-2)
        int ox = 4;
        int oz = -2;

        // Walls, floor, ceiling
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                boolean isEdge = x == 0 || x == 4 || z == 0 || z == 4;
                BlockPos floorOffset = new BlockPos(ox + x, -1, oz + z);
                blocks.add(new BlockPlacement(floorOffset, Blocks.COBBLESTONE.defaultBlockState()));

                if (isEdge) {
                    // 2-block-high walls
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 0, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                    blocks.add(new BlockPlacement(new BlockPos(ox + x, 1, oz + z), Blocks.COBBLESTONE.defaultBlockState()));
                }

                // Ceiling
                BlockPos ceilOffset = new BlockPos(ox + x, 2, oz + z);
                blocks.add(new BlockPlacement(ceilOffset, Blocks.COBBLESTONE.defaultBlockState()));
            }
        }

        // Door on south side center (x=2, z=4) - replace wall blocks with door
        BlockPos doorLower = new BlockPos(ox + 2, 0, oz + 4);
        BlockPos doorUpper = new BlockPos(ox + 2, 1, oz + 4);
        blocks.removeIf(bp -> bp.offset().equals(doorLower) || bp.offset().equals(doorUpper));
        blocks.add(new BlockPlacement(doorLower, Blocks.OAK_DOOR.defaultBlockState()));
        blocks.add(new BlockPlacement(doorUpper, Blocks.OAK_DOOR.defaultBlockState().setValue(
                net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
        )));

        // Furnace inside at offset (+5, 0, -1) from flag = (ox+1, 0, oz+1) inside room
        BlockPos furnaceOffset = new BlockPos(ox + 1, 0, oz + 1);
        blocks.add(new BlockPlacement(furnaceOffset, Blocks.FURNACE.defaultBlockState()));

        // Chest inside room
        BlockPos chestOffset = new BlockPos(ox + 3, 0, oz + 1);

        List<ItemStack> supplies = List.of(
                new ItemStack(Items.BEEF, 32),
                new ItemStack(Items.COAL, 32),
                new ItemStack(Items.STICK, 16)
        );

        TestExpectation expectation = new TestExpectation(
                List.of(
                        new ExpectedProduct("minecraft:cooked_beef", 3, 5),
                        new ExpectedProduct("minecraft:beef", -5, -3),
                        new ExpectedProduct("minecraft:coal", -4, -3)
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
}
