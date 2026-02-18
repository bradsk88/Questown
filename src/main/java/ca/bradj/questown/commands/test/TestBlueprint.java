package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public record TestBlueprint(
        RoomType roomType,
        Collection<BlockPlacement> blocks,
        Collection<ItemStack> supplyItems,
        BlockPos doorOrGateOffset,
        BlockPos chestOffset,
        ResourceLocation roomId,
        TestExpectation expectation,
        @Nullable BlockPos supplyDoorOffset
) {
    public TestBlueprint(
            RoomType roomType,
            Collection<BlockPlacement> blocks,
            Collection<ItemStack> supplyItems,
            BlockPos doorOrGateOffset,
            BlockPos chestOffset,
            ResourceLocation roomId,
            TestExpectation expectation
    ) {
        this(roomType, blocks, supplyItems, doorOrGateOffset, chestOffset,
             roomId, expectation, null);
    }

    public enum RoomType { FARM, INDOOR, WELCOME_MAT, BLOCK_ROOM }

    public record BlockPlacement(BlockPos offset, BlockState blockState) {}
}
