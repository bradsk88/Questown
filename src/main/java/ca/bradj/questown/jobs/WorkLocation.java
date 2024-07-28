package ca.bradj.questown.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiPredicate;
import java.util.function.Function;

public record WorkLocation(
        BiPredicate<Function<BlockPos, BlockState>, BlockPos> isJobBlock,
        ResourceLocation baseRoom
) {
    public static BiPredicate<Function<BlockPos, BlockState>, BlockPos> isBlock(Class<? extends Block> blockClass) {
        return (sl, bp) -> blockClass.isInstance(sl.apply(bp).getBlock());
    }
}
