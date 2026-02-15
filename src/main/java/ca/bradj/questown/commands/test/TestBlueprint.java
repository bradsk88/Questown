package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;

public record TestBlueprint(
        RoomType roomType,
        Collection<BlockPlacement> blocks,
        Collection<ItemStack> supplyItems,
        BlockPos doorOrGateOffset,
        BlockPos chestOffset,
        ResourceLocation roomId,
        TestExpectation expectation
) {
    public enum RoomType { FARM, INDOOR }

    public record BlockPlacement(BlockPos offset, BlockState blockState) {}
}
